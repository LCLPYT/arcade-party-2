package work.lclpnet.ap2.mode_default.util

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import work.lclpnet.ap2.ext.mc.displayName
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.color.PlayerColorPreferences
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.translate.Translations

private val SETTINGS_CODEC: MapCodec<Boolean> = Codec.BOOL.fieldOf("ap2:settings")
private val SETTINGS_ITEM = Items.COMMAND_BLOCK_MINECART
private const val SETTINGS_SLOT = 4
private const val RANDOM_SLOT = 22
private const val CANCEL_SLOT = 26

/**
 * A per-player settings item given during the preparation phase. Clicking it opens a nested
 * inventory menu hierarchy: settings -> preferred color (1st/2nd/3rd priority) -> color picker.
 * Every screen has a cancel button that returns to the previous one.
 *
 * The only setting for now is the preferred color, backed by [PlayerColorPreferences]. Preferences
 * form an ordered prefix: a tier can be edited only once the tier above it is set, and picking
 * "random" in a tier's color picker truncates that tier and all lower priorities.
 */
class SettingsMenu(
    private val translations: Translations,
    private val colorPreferences: PlayerColorPreferences
) {

    fun init(hooks: HookRegistrar) {
        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            if (player !is ServerPlayer) return@registerWith InteractionResult.PASS

            val stack = player.getItemInHand(hand)

            if (isSettingsItem(stack)) {
                openSettings(player)
                return@registerWith InteractionResult.SUCCESS_SERVER
            }

            InteractionResult.PASS
        }

        PlayerConnectionHooks.JOIN.registerWith(hooks) { player ->
            giveItem(player)
        }
    }

    fun giveItems(players: Iterable<ServerPlayer>) {
        for (it in players) {
            giveItem(it)
        }
    }

    fun giveItem(player: ServerPlayer) {
        val stack = ItemStack(SETTINGS_ITEM)

        stack.set(
            DataComponents.ITEM_NAME,
            translations.translateText(player, "ap2.settings")
                .withStyle(ChatFormatting.AQUA)
        )

        CustomNbt.set(stack, SETTINGS_CODEC, true)

        player.inventory.setItem(SETTINGS_SLOT, stack)
    }

    private fun isSettingsItem(stack: ItemStack): Boolean {
        if (!stack.isOf(SETTINGS_ITEM)) return false

        return CustomNbt.get(stack, SETTINGS_CODEC).orElse(false) ?: false
    }

    private fun openSettings(player: ServerPlayer) {
        val menu = ClickableMenu(1, translations.translateText(player, "ap2.settings"))

        val first = colorPreferences.get(player.uuid).firstOrNull()

        val colorIcon = ItemStack(if (first != null) Items.DYE.pick(first) else Items.NETHER_STAR).apply {
            set(
                DataComponents.ITEM_NAME,
                translations.translateText(player, "ap2.settings.color")
                    .withStyle(ChatFormatting.AQUA)
            )
        }

        menu.button(0, colorIcon) {
            openColorMenu(it)
        }

        menu.button(8, cancelIcon(player)) {
            it.closeContainer()
        }

        menu.open(player)
    }

    private fun openColorMenu(player: ServerPlayer) {
        val menu = ClickableMenu(1, translations.translateText(player, "ap2.settings.color"))

        val prefs = colorPreferences.get(player.uuid)
        val tierSlots = intArrayOf(2, 4, 6)

        for (tier in 0..2) {
            val slot = tierSlots[tier]

            when {
                tier < prefs.size -> {
                    val color = prefs[tier]

                    val icon = tierIcon(
                        player,
                        tier,
                        ItemStack(Items.DYE.pick(color)),
                        color.displayName()
                    )

                    menu.button(slot, icon) {
                        openColorPicker(it, tier)
                    }
                }

                tier == prefs.size -> {
                    val value = translations.translateText(player, "ap2.color.random").withStyle(ChatFormatting.GRAY)
                    val icon = tierIcon(player, tier, ItemStack(Items.NETHER_STAR), value)

                    menu.button(slot, icon) {
                        openColorPicker(it, tier)
                    }
                }

                else -> {
                    // lower tier locked until the tier above is set; unset tiers are random by default
                    val value = translations.translateText(player, "ap2.color.random").withStyle(ChatFormatting.GRAY)
                    val icon = tierIcon(player, tier, ItemStack(Items.FIREWORK_STAR), value)

                    icon.set(DataComponents.LORE, ItemLore(listOf(
                        translations.translateText(player, "ap2.color.pick_higher_priority_first")
                            .withStyle { style ->
                                style.withColor(ChatFormatting.RED).withItalic(false)
                            }
                    )))

                    menu.setItem(slot, icon)
                }
            }
        }

        menu.button(8, cancelIcon(player)) {
            openSettings(it)
        }

        menu.open(player)
    }

    private fun openColorPicker(player: ServerPlayer, tier: Int) {
        val prefs = colorPreferences.get(player.uuid)
        val otherTiers = prefs.filterIndexed { index, _ -> index != tier }.toSet()
        val available = DyeColor.entries.filter { it !in otherTiers }

        val menu = ClickableMenu(3, translations.translateText(player, tierKey(tier)))

        available.forEachIndexed { i, color ->
            val icon = ItemStack(Items.DYE.pick(color))
            icon.set(DataComponents.ITEM_NAME, color.displayName())

            menu.button(i, icon) {
                setTier(it, tier, color)
            }
        }

        val randomIcon = ItemStack(Items.NETHER_STAR).apply {
            set(DataComponents.ITEM_NAME, translations.translateText(player, "ap2.color.random"))
        }

        menu.button(RANDOM_SLOT, randomIcon) {
            truncate(it, tier)
        }

        menu.button(CANCEL_SLOT, cancelIcon(player)) {
            openColorMenu(it)
        }

        menu.open(player)
    }

    private fun setTier(player: ServerPlayer, tier: Int, color: DyeColor) {
        val prefs = colorPreferences.get(player.uuid).toMutableList()

        if (tier < prefs.size) {
            prefs[tier] = color
        } else {
            prefs.add(color)
        }

        colorPreferences.set(player.uuid, prefs)

        giveItem(player)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2f)

        openColorMenu(player)
    }

    private fun truncate(player: ServerPlayer, tier: Int) {
        val prefs = colorPreferences.get(player.uuid)

        colorPreferences.set(player.uuid, prefs.take(tier))

        giveItem(player)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2f)

        openColorMenu(player)
    }

    private fun tierIcon(player: ServerPlayer, tier: Int, base: ItemStack, value: Component): ItemStack {
        val name = Component.empty()
            .append(translations.translateText(player, tierKey(tier)))
            .append(Component.literal(": "))
            .append(value)

        base.set(DataComponents.ITEM_NAME, name)

        return base
    }

    private fun cancelIcon(player: ServerPlayer): ItemStack =
        ItemStack(Items.BARRIER).apply {
            set(
                DataComponents.ITEM_NAME,
                translations.translateText(player, "ap2.cancel")
                    .withStyle(ChatFormatting.RED)
            )
        }

    private fun tierKey(tier: Int): String = when (tier) {
        0 -> "ap2.color.tier.first"
        1 -> "ap2.color.tier.second"
        else -> "ap2.color.tier.third"
    }
}
