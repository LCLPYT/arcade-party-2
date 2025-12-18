package work.lclpnet.ap2.game.killeporter

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoubleBlockCombiner
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.material.Fluids
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.game.kit.KitHandle
import work.lclpnet.ap2.impl.game.kit.KitHandler
import work.lclpnet.ap2.impl.game.kit.PrefabKitLoader
import work.lclpnet.ap2.impl.util.CodecUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.hook.world.BlockModificationHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.lobby.game.api.prot.scope.EntityDamageSourceScope
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import work.lclpnet.lobby.game.map.GameMap
import java.lang.Math.floorMod
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.asJavaRandom

val MIN_DURATION_TICKS = Ticks.seconds(18)
val MAX_DURATION_TICKS = Ticks.seconds(32)
val GAME_DURATION_TICKS = Ticks.minutes(6)
const val TIME_TO_NIGHTFALL_DAYTIME_TICKS = 3600

data class LootEntry(val itemStack: ItemStack, val minCount: Int = 1, val maxCount: Int = 1) {

    fun generateItemStack(): ItemStack {
        val count = Random.nextInt(minCount, maxCount+1)
        return itemStack.copyWithCount(count)
    }

    companion object {
        val CODEC: Codec<LootEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter { it.itemStack },
                CodecUtil.POSITIVE_INT.fieldOf("min").orElse(1).forGetter { it.minCount },
                CodecUtil.POSITIVE_INT.fieldOf("max").orElse(1).forGetter { it.maxCount },
            ).apply(instance) { stack, i, j ->
                LootEntry(stack, min(i, j), max(i, j))
            }
        }
    }
}

data class LootTableEntry(val entry: LootEntry, val weight: Float) {

    companion object {
        val CODEC: Codec<LootTableEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                LootEntry.CODEC.fieldOf("entry").forGetter { it.entry },
                Codec.FLOAT.fieldOf("weight").forGetter { it.weight },
            ).apply(instance) { entry, weight ->
                LootTableEntry(entry, weight)
            }
        }
    }
}

data class LootTable(val entries: List<LootTableEntry>) {

    fun loadInto(list: WeightedList<LootEntry>) {
        for ((entry, weight) in entries) {
            list.add(entry, weight)
        }
    }

    companion object {
        val CODEC: Codec<LootTable> = RecordCodecBuilder.create { instance ->
            instance.group(
                LootTableEntry.CODEC.listOf().fieldOf("entries").forGetter { it.entries }
            ).apply(instance) { entries ->
                LootTable(entries)
            }
        }
    }
}

class KilleporterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    var kitHandler: KitHandler? = null
    var kitLoader: PrefabKitLoader? = null
    var itemUseAllowed = false
    val filledInventories = mutableSetOf<BlockPos>()
    val inventoryContent = WeightedList<LootEntry>()

    init {
        useSurvivalMode()
    }

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        kitLoader = PrefabKitLoader(world.registryAccess(), gameHandle.logger)

        val kitFuture = kitLoader!!.loadHotbar(this)

        val lootFuture = CompletableFuture.runAsync {
            val lootTable = loadLootTable()

            lootTable?.loadInto(inventoryContent)
        }

        return CompletableFuture.allOf(kitFuture, lootFuture)
    }

    fun loadLootTable(): LootTable? {
        this::class.java.getResourceAsStream("/loot/containers.json").use {
            if (it == null) return@use null

            val content = String(it.readAllBytes(), StandardCharsets.UTF_8)
            val json = Gson().fromJson(content, JsonObject::class.java)

            return LootTable.CODEC.decode(JsonOps.INSTANCE, json)
                .resultOrPartial { err -> gameHandle.logger.error("Failed to parse loot table: {}", err) }
                .map { res -> res.first }
                .orElse(null)
        }

        return null
    }

    override fun prepare() {

        world.setDayTime((13000 - TIME_TO_NIGHTFALL_DAYTIME_TICKS).toLong())

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, true)
            .set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0)
            .set(GameRules.SPAWN_PHANTOMS, false)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, true)
            .set(GameRules.KEEP_INVENTORY, false)
            .set(GameRules.ADVANCE_TIME, false)
            .set(GameRules.SPAWN_MOBS, true)
            .set(GameRules.MOB_DROPS, true)
            .set(GameRules.MOB_GRIEFING, true)
            .set(GameRules.ENTITY_DROPS, true)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)

        useRemainingPlayersDisplay()
        useSmoothDeath()
        setupKits()

        gameHandle.hooks.registerHook(
            ServerLivingEntityHooks.ALLOW_DAMAGE,
            ServerLivingEntityEvents.AllowDamage { entity, _, _ ->

                if (entity is ServerPlayer && entity.foodData.foodLevel >= 20) {
                    entity.foodData.addExhaustion(8f)
                    entity.foodData.setSaturation(2f)
                }
                true
            }
        )
    }

    override fun afterInitialDelay() {
        kitHandler?.startKitSelectionTimer(commons(), Ticks.seconds(15)) {super.afterInitialDelay()}
    }

    override fun go() {

        kitHandler?.disableKitChanger()

        itemUseAllowed = true

        commons().gameRuleBuilder().set(GameRules.ADVANCE_TIME, true)

        gameHandle.protect { config ->
            config.allowAll()
            config.disallow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope { entity, source ->
                entity is ServerPlayer && source.entity is ServerPlayer
                        && !source.`is`(DamageTypes.PLAYER_EXPLOSION)
                        && !source.`is`(DamageTypes.INDIRECT_MAGIC)
                        && !source.`is`(DamageTypes.MAGIC)
            })
        }

        gameHandle.hooks.registerHook(
            BlockModificationHooks.PLACE_FLUID,
            BlockModificationHooks.FluidTransferHook { _, pos, entity, fluid ->
            val minDistSq = 6.0 * 6.0
            entity is ServerPlayer && fluid.isSame(Fluids.LAVA) && players().any {
                it != entity && it.distanceToSqr(pos.center) < minDistSq
            }
        })

        gameHandle.hooks.registerHook(
            PlayerInteractionHooks.USE_BLOCK,
            UseBlockCallback { player, world, _, hitResult ->
                onUseInventory(player, world, hitResult.blockPos)
                InteractionResult.PASS
            }
        )

        gameHandle.hooks.registerHook(
            BlockModificationHooks.BREAK_BLOCK,
            BlockModificationHooks.BlockModifyHook {world, pos, entity ->
                if (entity !is ServerPlayer || !world.getBlockState(pos).`is`(Blocks.DECORATED_POT)) {return@BlockModifyHook false}
                onUseInventory(entity, world, pos)
                return@BlockModifyHook false
            }
        )

        gameHandle.hooks.registerHook(
            BlockModificationHooks.PLACE_BLOCK,
            BlockModificationHooks.PlaceBlockHook { _, pos, entity, _ ->
                if (entity !is ServerPlayer) {return@PlaceBlockHook false}
                filledInventories.add(pos)
                return@PlaceBlockHook false
            }
        )

        switchTimeout()

        gameHandle.scheduler.interval(20*60*3, 20*60*3, Runnable {
            SoundHelper.playSound(world, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.5f)
            translate("game.ap2.killeporter.chest_refill").formatted(ChatFormatting.AQUA).sendTo(allPlayers())
            filledInventories.clear()
        })

        timeout(GAME_DURATION_TICKS) {
            winManager.forceWin(players().toSet())
        }
    }

    private fun onUseInventory(player: Player, world: Level, pos: BlockPos) {

        if (player !is ServerPlayer || !gameHandle.participants.isParticipating(player)) return

        val blockEntity = world.getBlockEntity(pos)
        val state = world.getBlockState(pos)
        val block = state.block
        val inventoryToFill: Container?

        if (blockEntity is Container && filledInventories.add(pos)) {

            if (block is ChestBlock) {
                inventoryToFill = ChestBlock.getContainer(block, state, world, pos, false)
                if (ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
                    val neighborDir = ChestBlock.getConnectedDirection(state)
                    val otherPos = pos.relative(neighborDir)
                    filledInventories.add(otherPos)
                }
            }
            else inventoryToFill = blockEntity

            fillInventory(inventoryToFill!!, state)
        }
    }

    private fun fillInventory(inventory: Container, state: BlockState) {

        inventory.clearContent()

        val invSize = inventory.containerSize
        val availableSlots = (0..<invSize).toMutableList()
        val maxSlotsToFill = 5.coerceAtMost(invSize)

        val slotsToFill = if (state.block == Blocks.DECORATED_POT) {
            Random.nextInt(0, maxSlotsToFill + 1)
        } else { Random.nextInt(1, maxSlotsToFill + 1) }

        repeat(slotsToFill) {
            val slot = availableSlots.removeAt(Random.nextInt(availableSlots.size))
            val entry = inventoryContent.getRandomElement(Random.asJavaRandom())
            inventory.setItem(slot, entry!!.generateItemStack())
        }
    }

    fun switchTimeout() {

        val maxDelaySeconds = 7
        val switchTime =  Random.nextInt(MIN_DURATION_TICKS, MAX_DURATION_TICKS+1)
        val messageTime = Random.nextInt(Ticks.seconds(1), Ticks.seconds(maxDelaySeconds)+1)

        timeout(switchTime - messageTime) {
            translate("game.ap2.killeporter.switch_announcement", FormatWrapper.styled(maxDelaySeconds, ChatFormatting.YELLOW))
            .formatted(ChatFormatting.GREEN)
            .sendTo(players(), true)}

        timeout(switchTime) {
            playerSwitcher()
            switchTimeout()
        }
    }

    fun switchAnnouncement() {
        timeout(MIN_DURATION_TICKS) {

            switchAnnouncement()
        }
    }

    fun playerSwitcher() {
        val shuffledPlayers = players().shuffled()
        val playerCount = shuffledPlayers.count()
        val positionRotations = shuffledPlayers.map { player ->
            PositionRotation(player.x, player.y, player.z, player.yRot, player.xRot)
        }

        for (p in (0 ..< playerCount)) {
            val previousIndex = floorMod(p-1, playerCount)
            shuffledPlayers[p].teleport(positionRotations[previousIndex])
            translate("game.ap2.killeporter.switch_message", shuffledPlayers[previousIndex].scoreboardName)
                .formatted(ChatFormatting.GREEN)
                .sendTo(shuffledPlayers[p], true)
        }
    }

    private fun setupKits() {
        kitHandler = KitHandler.create(gameHandle, world) { kitHandle: KitHandle -> kitLoader!!.createKits(kitHandle) }

        kitHandler?.manager?.modifyOptions {
            it.withKitSelectorSlot(8)
        }

        gameHandle.getHooks().registerHook(
            PlayerInteractionHooks.USE_ITEM,
            UseItemCallback { player: Player, _: Level, hand: InteractionHand ->
                if (player !is ServerPlayer) return@UseItemCallback InteractionResult.PASS

                val stack = player.getItemInHand(hand)

                if (itemUseAllowed || kitHandler!!.isKitSelector(stack)) {
                    return@UseItemCallback InteractionResult.PASS
                }

                if (stack.has(DataComponents.USE_COOLDOWN)) {
                    player.cooldowns.addCooldown(stack, 0)
                }

                PlayerUtils.syncPlayerItems(player)
                InteractionResult.FAIL
            })

        kitHandler?.setup()
    }
}
