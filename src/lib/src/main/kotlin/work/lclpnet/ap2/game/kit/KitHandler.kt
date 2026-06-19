package work.lclpnet.ap2.game.kit

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.base.MapGameInstance
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.inv.prompt.OptionPrompt
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val KIT_SELECTOR_CODEC: MapCodec<Boolean> = Codec.BOOL.fieldOf("ap2:kit_selector")
private val KIT_SELECTOR_ITEM = Items.NETHER_STAR

class KitHandler(
    val manager: KitManager,
    private val participants: Participants,
    private val kitHandle: KitHandle
) {
    private val mayChangeKit: MutableSet<UUID> = HashSet<UUID>()

    fun init(hooks: HookRegistrar) {
        PlayerInteractionHooks.USE_ITEM.registerWith(
            hooks,
            UseItemCallback { player: Player, _, hand ->
                if (player !is ServerPlayer || !participants.isParticipating(player)) {
                    return@UseItemCallback InteractionResult.PASS
                }

                val stack = player.getItemInHand(hand)

                if (isKitSelector(stack) && canChangeKit(player)) {
                    openKitSelector(player)
                    return@UseItemCallback InteractionResult.SUCCESS_SERVER
                }

                InteractionResult.PASS
            })
    }

    @Synchronized
    fun openKitSelector(player: ServerPlayer) {
        val title = kitHandle.translations.translateText(player, "ap2.kit_selector")

        OptionPrompt.open(player, title, manager.kits) {
            kitHandle.createKitIcon(it, player)
        }.thenAccept { optKit ->
            optKit.ifPresent { kit: Kit ->
                changeKit(player, kit)
            }
        }
    }

    @Synchronized
    fun changeKit(player: ServerPlayer, kit: Kit) {
        if (!mayChangeKit.contains(player.getUUID())) return

        manager.changeKit(player, kit)

        kitHandle.translations.translateText(
            "ap2.kit_selector.selected",
            kitHandle.kitName(kit).withStyle(ChatFormatting.AQUA)
        )
            .withStyle(ChatFormatting.GREEN)
            .sendTo(player)

        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2f)
    }

    @Synchronized
    fun canChangeKit(player: ServerPlayer): Boolean =
        mayChangeKit.contains(player.getUUID())

    fun setupPlayerKits() {
        manager.setupPlayerKits(participants)
    }

    fun enableKitChanger() {
        participants.forEach {
            enableKitChanger(it)
        }
    }

    @Synchronized
    fun enableKitChanger(player: ServerPlayer) {
        mayChangeKit.add(player.getUUID())

        val stack = ItemStack(KIT_SELECTOR_ITEM)

        stack.set(
            DataComponents.ITEM_NAME,
            kitHandle.translations.translateText(player, "ap2.kit_selector")
                .withStyle(ChatFormatting.AQUA)
        )

        CustomNbt.set(stack, KIT_SELECTOR_CODEC, true)

        player.inventory.setItem(manager.options.kitSelectorSlot, stack)
    }

    fun disableKitChanger() {
        participants.forEach {
            disableKitChanger(it)
        }
    }

    @Synchronized
    fun disableKitChanger(player: ServerPlayer) {
        mayChangeKit.remove(player.getUUID())

        closeKitChanger(player)

        player.inventory.removeItemNoUpdate(manager.options.kitSelectorSlot)
    }

    fun closeKitChanger() {
        participants.forEach {
            closeKitChanger(it)
        }
    }

    fun closeKitChanger(player: ServerPlayer) {
        player.closeContainer()
    }

    fun isKitSelector(stack: ItemStack): Boolean {
        if (!stack.isOf(KIT_SELECTOR_ITEM)) return false

        return CustomNbt.get(stack, KIT_SELECTOR_CODEC).orElse(false) ?: false
    }

    fun selectKitChanger() {
        for (player in participants) {
            PlayerInventoryAccess.setSelectedSlot(player, manager.options.kitSelectorSlot)
        }
    }

    fun selectKitItem() {
        for (player in participants) {
            PlayerInventoryAccess.setSelectedSlot(player, manager.options.mainItemSlot)
        }
    }

    fun startKitSelectionTimer(gameInstance: MapGameInstance, announcer: Announcer, onComplete: Runnable) {
        startKitSelectionTimer(gameInstance, announcer, 10.seconds, onComplete)
    }

    fun startKitSelectionTimer(gameInstance: MiniGameInstance, announcer: Announcer, duration: Duration, onComplete: Runnable) {
        if (manager.kits.size < 2) {
            onComplete.run()
            return
        }

        announcer.announceSubtitle("ap2.kit_selector.hint")

        selectKitChanger()

        val label = kitHandle.translations.translateText("ap2.kit_selection")

        gameInstance.createTimer(label, duration).whenDone(onComplete)
    }

    /**
     * Performs common kit setup.
     * This includes:
     * - initializing the kit manager
     * - initializing all registered kits
     * - enabling the kit changer item, giving the item to the players
     */
    fun setup() {
        manager.init()

        init(kitHandle.hooks)
        setupPlayerKits()
        enableKitChanger()
        selectKitChanger()
    }

    fun reequip(player: ServerPlayer) {
        manager.getKit(player).unequip(player, manager.options)
        manager.getKit(player).equip(player, manager.options)
    }

    companion object {

        @JvmStatic
        fun create(
            gameHandle: MiniGameHandle,
            world: ServerLevel,
            kitsFactory: (KitHandle) -> List<Kit>
        ): KitHandler {
            val readView = ProxyKitReadView()
            val handle: DefaultKitHandle = DefaultKitHandle.of(gameHandle, world.registryAccess(), readView)

            val manager = KitManager(kitsFactory(handle))

            readView.inject(manager)

            return KitHandler(manager, gameHandle.participants, handle)
        }
    }
}
