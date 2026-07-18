package work.lclpnet.ap2.game.item

import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.DamageResistant
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.phys.Vec3
import org.json.JSONObject
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.IconMaker
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpecialItems(
    private val gameHandle: MiniGameHandle,
    private val map: GameMap,
    private val level: ServerLevel,
    private val random: Random,
    private val positions: SpecialItemPositions,
    private val registry: SpecialItemRegistry,
) : SpecialItemContext {
    private val scene: SpecialItemScene = SpecialItemScene(random, level)
    private var weightedItems: WeightedList<SpecialItem> = WeightedList.empty()

    override val scheduler: TaskScheduler = gameHandle.scheduler
    override val translations: Translations = gameHandle.translations

    var despawnTicks = 600
    var spawnMinTicks = Ticks.seconds(4)
    var spawnMaxTicks = Ticks.seconds(7)
    var maxItems = 16
    var markGlowing = false
    var itemSlot = 8
    var itemSize = SpecialItemObject.DEFAULT_SIZE

    fun positions(): SpecialItemPositions = positions

    fun init() {
        val cfg = map.getProperty<Any?>("items") as? JSONObject ?: JSONObject()
        val overrides = cfg.optJSONObject("overrides")

        weightedItems = registry.weightedItems(overrides ?: JSONObject())

        spawnMinTicks = max(1, cfg.optNumber("spawn-min-ticks", spawnMinTicks).toInt())
        spawnMaxTicks = max(1, cfg.optNumber("spawn-max-ticks", spawnMaxTicks).toInt())
        despawnTicks = cfg.optNumber("despawn-ticks", despawnTicks).toInt()
        maxItems = cfg.optNumber("max-items", maxItems).toInt()

        // games without a spawn area in their map config spawn items at positions of their own choosing
        if (cfg.has("spawn-area")) {
            positions.setShape(getSpawnArea(map))
        }

        scene.init(gameHandle.rootScheduler, gameHandle.hooks)
    }

    fun setup() {
        positions.update()

        scene.onPickup().register(::pickup)

        val hooks = gameHandle.hooks
        val scheduler = gameHandle.scheduler

        PlayerInventoryHooks.DROP_ITEM.registerWith(hooks, ::onDropItem)

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            interact(player, hand)
        }

        PlayerInventoryHooks.SWAP_HANDS.registerWith(hooks) { player, _ ->
            swapHands(player)
        }

        PlayerSwingHandHook.HOOK.registerWith(hooks) { player, hand ->
            onSwingHand(player, hand)
        }

        for (item in registry.entries()) {
            item.registerHooks(hooks, this)
            item.scheduleTasks(scheduler, this)
        }

        scheduler.interval(1) { ->
            tickPickup()
        }
    }

    private fun swapHands(player: ServerPlayer): Boolean {
        val stack = player.inventory.getItem(itemSlot)
        val item = get(stack) ?: return false

        val result = useItem(player, stack, item, null)

        return result !== InteractionResult.PASS
    }

    private fun onDropItem(player: Player, slotIdx: Int, inInventory: Boolean): Boolean {
        if (player !is ServerPlayer) return false

        val stack = if (inInventory) {
            val slot = player.containerMenu.getSlot(slotIdx)
            slot.item
        } else {
            player.inventory.getItem(slotIdx)
        }

        val item = get(stack) ?: return false

        if (!item.canBeDropped(player, stack)) return true

        player.inventory.setItem(itemSlot, ItemStack.EMPTY)
        dropSpecialItem(player, item, stack)
        item.onDropped(player)

        return true
    }

    private fun dropSpecialItem(player: ServerPlayer, item: SpecialItem, stack: ItemStack) {
        val pos = player.eyePosition.subtract(0.0, 0.3, 0.0)

        val dropStack = configureStack(item, item.usedItemStack(stack, level.registryAccess()))

        val obj = scene.spawnItem(pos, item, dropStack, gameHandle.translations, itemName(item), itemSize)
        obj.setPickupDelay(40)

        scheduleDespawn(obj)

        val pitchSin = Mth.sin((player.xRot * (Math.PI / 180.0).toFloat()).toDouble())
        val pitchCos = Mth.cos((player.xRot * (Math.PI / 180.0).toFloat()).toDouble())
        val yawSin = Mth.sin((player.yRot * (Math.PI / 180.0).toFloat()).toDouble())
        val yawCos = Mth.cos((player.yRot * (Math.PI / 180.0).toFloat()).toDouble())
        val randomHorizontalAngle = random.nextFloat() * (Math.PI * 2).toFloat()
        val divergence = 0.02f * random.nextFloat()

        scene.velocity(obj).set(
            (-yawSin * pitchCos * 0.3f + Mth.cos(randomHorizontalAngle.toDouble()) * divergence).toDouble(),
            (-pitchSin * 0.3f + 0.1f + (random.nextFloat() - random.nextFloat()) * 0.1f).toDouble(),
            (yawCos * pitchCos * 0.3f + Mth.sin(randomHorizontalAngle.toDouble()) * divergence).toDouble()
        ).mul(20.0)
    }

    private fun interact(p: Player, hand: InteractionHand): InteractionResult {
        if (p !is ServerPlayer) return InteractionResult.PASS

        val stack = p.getItemInHand(hand)
        val item = get(stack) ?: return InteractionResult.PASS

        return useItem(p, stack, item, hand)
    }

    private fun useItem(
        player: ServerPlayer,
        stack: ItemStack,
        item: SpecialItem,
        hand: InteractionHand?,
    ): InteractionResult {
        if (player.cooldowns.isOnCooldown(stack)) return InteractionResult.FAIL

        return item.onUse(player, stack, hand, this)
    }

    private fun onSwingHand(player: ServerPlayer, hand: InteractionHand) {
        val stack = player.getItemInHand(hand)
        val item = get(stack)

        if (item == null || player.cooldowns.isOnCooldown(stack)) return

        item.onSwing(player, stack, hand, this)
    }

    private fun tickPickup() {
        for (player in gameHandle.participants) {
            scene.tickPickUp(player)
        }
    }

    private fun pickup(player: ServerPlayer, obj: SpecialItemObject): Boolean {
        val item = obj.item()

        // check if the player already has a special item
        if (hasAnySpecialItem(player) && item.shouldTransferToInventory(player)) return false

        if (!item.canBePickedUp(player)) return false

        val stack = obj.itemDisplay().stack.copy()

        stack.set(DataComponents.CUSTOM_NAME, itemName(item).translateFor(player))

        itemDescription(player, item)?.let { desc ->
            val lore = IconMaker.wrapText(desc, 32)
            stack.set(DataComponents.LORE, ItemLore(lore))
            player.sendOverlayMessage(Component.literal("↓ ")
                .withStyle(ChatFormatting.AQUA)
                .append(desc))
        }

        if (item.shouldTransferToInventory(player)) {
            player.inventory.setItem(itemSlot, stack)
        }

        item.onPickedUp(player, stack, this)

        return true
    }

    private fun itemName(item: SpecialItem): TranslatedText {
        val gameId = gameHandle.gameInfo.id
        val key = listOf("game", gameId.namespace, gameId.path, "item", item.id).joinToString(".")

        return gameHandle.translations.translateText(key)
            .withStyle { style ->
                style.withItalic(false).applyFormat(Rarity.UNCOMMON.color())
            }
    }

    private fun itemDescription(player: ServerPlayer?, item: SpecialItem): Component? {
        val gameId = gameHandle.gameInfo.id
        val key = listOf("game", gameId.namespace, gameId.path, "item", item.id, "desc").joinToString(".")

        if (!gameHandle.translations.translator.hasTranslation("en_us", key)) {
            return null
        }

        return gameHandle.translations.translateText(player, key)
            .withStyle { style ->
                style.withItalic(false).applyFormat(ChatFormatting.GREEN)
            }
    }

    fun hasAnySpecialItem(player: ServerPlayer): Boolean =
        !hasSpecialItem(player, null)

    override fun hasSpecialItem(player: ServerPlayer, item: SpecialItem?): Boolean =
        get(player.inventory.getItem(itemSlot)) === item

    override fun removeSpecialItem(player: ServerPlayer, item: SpecialItem) {
        val currentItem = get(player.inventory.getItem(itemSlot))

        if (currentItem === item) {
            player.inventory.setItem(itemSlot, ItemStack.EMPTY)
        }
    }

    override fun isSpecialItem(stack: ItemStack, item: SpecialItem): Boolean =
        get(stack) === item


    fun get(stack: ItemStack): SpecialItem? {
        val nbt = CustomNbt.get(stack, NBT_CODEC).orElse(null) ?: return null

        val id = nbt.getStringOr(ID_KEY, "")

        return registry[id]
    }

    private fun configureStack(item: SpecialItem, stack: ItemStack): ItemStack {
        // persist special item id in the stack
        val nbt = CompoundTag()
        nbt.putString(ID_KEY, item.id)

        CustomNbt.set(stack, NBT_CODEC, nbt)

        stack.set(
            DataComponents.DAMAGE_RESISTANT, DamageResistant(
                level.registryAccess().getOrThrow(DamageTypeTags.IS_FIRE)
            )
        )

        stack.set(DataComponents.RARITY, Rarity.UNCOMMON)

        return stack
    }

    fun spawnRandomItem() {
        if (scene.itemCount() >= maxItems) return

        val worldBorder = level.worldBorder
        var blockPos: BlockPos?
        var i = 0

        do {
            blockPos = positions.randomPos(random)

            if (blockPos == null) return
        } while (++i < 16 && !worldBorder.isWithinBounds(blockPos))

        spawnRandomItemAt(blockPos)
    }

    fun spawnRandomItemAt(blockPos: BlockPos): SpecialItemObject? {
        if (scene.itemCount() >= maxItems) return null

        val pos = Vec3.atBottomCenterOf(blockPos)

        if (!level.worldBorder.isWithinBounds(pos)) return null

        val item = weightedItems.getRandomElement(random) ?: return null

        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 20, 0.1, 0.1, 0.1, 0.1)
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 400, 0.15, 4.0, 0.15, 0.1)

        val stack = configureStack(item, item.createItemStack(level.registryAccess()))

        val obj = scene.spawnItem(pos, item, stack, gameHandle.translations, itemName(item), itemSize)

        if (markGlowing) {
            obj.setGlowing(true)
        }

        scheduleDespawn(obj)

        return obj
    }

    operator fun contains(obj: SpecialItemObject?): Boolean =
        scene.contains(obj)

    private fun scheduleDespawn(obj: SpecialItemObject) {
        if (despawnTicks <= 0) return

        var timer = 0
        var particle = 0

        gameHandle.scheduler.interval(1) { task ->
            if (!scene.contains(obj)) {
                task.cancel()
                return@interval
            }

            val t = timer++

            if (t == particle) {
                particle = timer + ITEM_PARTICLE_MIN_TICKS +
                        random.nextInt(ITEM_PARTICLE_MAX_TICKS - ITEM_PARTICLE_MIN_TICKS + 1)

                level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    obj.position.x,
                    obj.position.y + 0.125,
                    obj.position.z,
                    1,
                    0.35,
                    0.25,
                    0.35,
                    0.1
                )
            }

            if (t >= despawnTicks) {
                scene.remove(obj)
                task.cancel()
            }
        }
    }

    fun spawnPeriodically() {
        fun randomInterval(): Int {
            val minTicks = min(spawnMinTicks, spawnMaxTicks)
            val maxTicks = max(spawnMinTicks, spawnMaxTicks)
            return random.nextInt(maxTicks - minTicks + 1) + minTicks
        }

        var timer = 0
        var next = randomInterval()

        gameHandle.scheduler.interval(1) { ->
            if (timer++ < next) return@interval

            timer = 0
            next = randomInterval()
            spawnRandomItem()
        }
    }

    fun syncWithWorldBorder() {
        val border = level.worldBorder

        var prevSize = Double.NaN
        var prevCenterX = Double.NaN
        var prevCenterZ = Double.NaN

        gameHandle.scheduler.interval(20) { ->
            val size = border.size
            val centerX = border.centerX
            val centerZ = border.centerZ

            if (abs(prevSize - size) < 0.1 || abs(prevCenterX - centerX) < 0.1 || abs(prevCenterZ - centerZ) < 0.1) {
                prevSize = size
                prevCenterX = centerX
                prevCenterZ = centerZ

                positions.update()
            }
        }
    }

    companion object {
        val NBT_CODEC: MapCodec<CompoundTag> = CompoundTag.CODEC.fieldOf("ap2:special_item")
        const val ID_KEY = "Id"
        private const val ITEM_PARTICLE_MIN_TICKS = 22
        private const val ITEM_PARTICLE_MAX_TICKS = 38

        fun getSpawnArea(map: GameMap): BlockShape {
            val mapSpawn = BlockPos.containing(MapUtils.getSpawnPosition(map))

            val cfg = map.requireProperty<JSONObject>("items")
            val areaJson = cfg.getJSONObject("spawn-area")

            return MapUtil.readShape(areaJson, mapSpawn)
        }

        fun create(
            gameHandle: MiniGameHandle,
            map: GameMap,
            world: ServerLevel,
            random: Random,
            debugController: DebugController,
            config: (SpecialItemRegistrar) -> Unit,
        ): SpecialItems {
            val validSpawn = BlockPredicate.and(
                { pos ->
                    gameHandle.worldBorderManager.worldBorder.isWithinBounds(pos)
                },
                WalkableBlockPredicate(world)
            )

            return create(gameHandle, map, world, random, validSpawn, debugController, config)
        }

        fun create(
            gameHandle: MiniGameHandle,
            map: GameMap,
            world: ServerLevel,
            random: Random,
            validSpawn: BlockPredicate,
            debugController: DebugController,
            config: (SpecialItemRegistrar) -> Unit,
        ): SpecialItems {
            val positions = SpecialItemPositions(validSpawn, debugController)
            val registry = SpecialItemRegistry()

            config(registry)

            val specialItems = SpecialItems(gameHandle, map, world, random, positions, registry)

            specialItems.init()

            return specialItems
        }
    }
}
