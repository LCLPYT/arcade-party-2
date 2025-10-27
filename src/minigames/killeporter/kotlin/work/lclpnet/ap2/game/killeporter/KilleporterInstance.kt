package work.lclpnet.ap2.game.killeporter

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.DoubleBlockProperties
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluids
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameRules
import net.minecraft.world.World
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.impl.ds.WeightedList
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.game.kit.KitHandle
import work.lclpnet.ap2.impl.game.kit.KitHandler
import work.lclpnet.ap2.impl.game.kit.PrefabKitLoader
import work.lclpnet.ap2.players
import work.lclpnet.ap2.teleport
import work.lclpnet.ap2.timeout
import work.lclpnet.ap2.translate
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
import java.util.concurrent.CompletableFuture
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
}

class KilleporterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    var kitHandler: KitHandler? = null
    var kitLoader: PrefabKitLoader? = null
    var itemUseAllowed = false
    val filledInventories = mutableSetOf<BlockPos>()
    val inventoryContent = WeightedList<LootEntry>()

    init {
        useSurvivalMode()

        inventoryContent.add(LootEntry(ItemStack(Items.TNT), maxCount = 4), 0.1f)
        inventoryContent.add(LootEntry(ItemStack(Items.COBWEB), maxCount = 6), 0.2f)
        inventoryContent.add(LootEntry(ItemStack(Items.IRON_PICKAXE)), 0.05f)
        inventoryContent.add(LootEntry(ItemStack(Items.IRON_HELMET)), 0.05f)
        inventoryContent.add(LootEntry(ItemStack(Items.IRON_CHESTPLATE)), 0.05f)
        inventoryContent.add(LootEntry(ItemStack(Items.FLINT_AND_STEEL)), 0.05f)
        inventoryContent.add(LootEntry(ItemStack(Items.ENDER_PEARL)), 0.05f)
        inventoryContent.add(LootEntry(ItemStack(Items.SAND), minCount = 3, maxCount = 16), 0.3f)
        inventoryContent.add(LootEntry(ItemStack(Items.WHITE_WOOL), minCount = 3, maxCount = 16), 0.3f)
    }

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        kitLoader = PrefabKitLoader(world.registryManager, gameHandle.logger)

        return kitLoader!!.loadHotbar(this)
    }

    override fun prepare() {

        world.timeOfDay = (13000 - TIME_TO_NIGHTFALL_DAYTIME_TICKS).toLong()

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, true)
            .set(GameRules.DO_FIRE_TICK, true)
            .set(GameRules.DO_INSOMNIA, false)
            .set(GameRules.NATURAL_REGENERATION, true)
            .set(GameRules.KEEP_INVENTORY, false)
            .set(GameRules.DO_DAYLIGHT_CYCLE, false)
            .set(GameRules.DO_MOB_SPAWNING, true)
            .set(GameRules.DO_MOB_LOOT, true)
            .set(GameRules.DO_MOB_GRIEFING, true)
            .set(GameRules.DO_ENTITY_DROPS, true)
            .set(GameRules.ANNOUNCE_ADVANCEMENTS, false)

        useRemainingPlayersDisplay()
        useSmoothDeath()
        setupKits()

        gameHandle.hooks.registerHook(
            ServerLivingEntityHooks.ALLOW_DAMAGE,
            ServerLivingEntityEvents.AllowDamage { entity, _, _ ->

                if (entity is ServerPlayerEntity && entity.hungerManager.foodLevel >= 20) {
                    entity.hungerManager.addExhaustion(8f)
                    entity.hungerManager.saturationLevel = 2f
                }
                true
            }
        )
    }

    override fun afterInitialDelay() {
        kitHandler?.startKitSelectionTimer(commons(), Ticks.seconds(15)) {super.afterInitialDelay()}
    }

    override fun ready() {

        kitHandler?.disableKitChanger()

        itemUseAllowed = true

        commons().gameRuleBuilder().set(GameRules.DO_DAYLIGHT_CYCLE, true)

        gameHandle.protect { config ->
            config.allowAll()
            config.disallow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope { entity, source ->
                entity is ServerPlayerEntity && source.attacker is ServerPlayerEntity
                        && !source.isOf(DamageTypes.PLAYER_EXPLOSION)
                        && !source.isOf(DamageTypes.INDIRECT_MAGIC)
                        && !source.isOf(DamageTypes.MAGIC)
            })
        }

        gameHandle.hooks.registerHook(
            BlockModificationHooks.PLACE_FLUID,
            BlockModificationHooks.FluidTransferHook { world, pos, entity, fluid ->
            val minDistSq = 6.0 * 6.0
            entity is ServerPlayerEntity && fluid.matchesType(Fluids.LAVA) && players().any {
                it != entity && it.squaredDistanceTo(pos.toCenterPos()) < minDistSq
            }
        })

        gameHandle.hooks.registerHook(
            PlayerInteractionHooks.USE_BLOCK,
            UseBlockCallback { player, world, hand, hitResult ->
                onUseInventory(player, world, hitResult.blockPos)
                ActionResult.PASS
            }
        )

        gameHandle.hooks.registerHook(
            BlockModificationHooks.BREAK_BLOCK,
            BlockModificationHooks.BlockModifyHook {world, pos, entity ->
                if (entity !is ServerPlayerEntity || !world.getBlockState(pos).isOf(Blocks.DECORATED_POT)) {return@BlockModifyHook false}
                onUseInventory(entity, world, pos)
                return@BlockModifyHook false
            }
        )

        gameHandle.hooks.registerHook(
            BlockModificationHooks.PLACE_BLOCK,
            BlockModificationHooks.PlaceBlockHook {world, pos, entity, state ->
                if (entity !is ServerPlayerEntity) {return@PlaceBlockHook false}
                filledInventories.add(pos)
                return@PlaceBlockHook false
            }
        )

        switchTimeout()

        timeout(GAME_DURATION_TICKS) { ->
            winManager.forceWin(players().toSet())
        }
    }

    private fun onUseInventory(player: PlayerEntity, world: World, pos: BlockPos) {

        if (player !is ServerPlayerEntity || !gameHandle.participants.isParticipating(player)) return

        val blockEntity = world.getBlockEntity(pos)
        val state = world.getBlockState(pos)
        val block = state.block
        val inventoryToFill: Inventory?

        if (blockEntity is Inventory && filledInventories.add(pos)) {

            if (block is ChestBlock) {
                inventoryToFill = ChestBlock.getInventory(block, state, world, pos, false)
                if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
                    val neighborDir = ChestBlock.getFacing(state)
                    val otherPos = pos.offset(neighborDir)
                    filledInventories.add(otherPos)
                }
            }
            else inventoryToFill = blockEntity

            fillInventory(inventoryToFill!!, state)
        }
    }

    private fun fillInventory(inventory: Inventory, state: BlockState) {

        inventory.clear()

        val invSize = inventory.size()
        val availableSlots = (0..invSize - 1).toMutableList()
        val maxSlotsToFill = 5.coerceAtMost(invSize)

        val slotsToFill = if (state.block == Blocks.DECORATED_POT) {
            Random.nextInt(0, maxSlotsToFill + 1)
        } else { Random.nextInt(1, maxSlotsToFill + 1) }

        repeat(slotsToFill) {
            val slot = availableSlots.removeAt(Random.nextInt(availableSlots.size))
            val entry = inventoryContent.getRandomElement(Random.asJavaRandom())
            inventory.setStack(slot, entry!!.generateItemStack())
        }
    }

    fun switchTimeout() {

        val maxDelaySeconds = 7
        val switchTime =  Random.nextInt(MIN_DURATION_TICKS, MAX_DURATION_TICKS+1)
        val messageTime = Random.nextInt(Ticks.seconds(1), Ticks.seconds(maxDelaySeconds)+1)

        timeout(switchTime - messageTime) { ->
            translate("game.ap2.killeporter.switch_announcement", FormatWrapper.styled(maxDelaySeconds, Formatting.YELLOW))
            .formatted(Formatting.GREEN)
            .sendTo(players(), true)}

        timeout(switchTime) { ->
            playerSwitcher()
            switchTimeout()
        }
    }

    fun switchAnnouncement() {
        timeout(MIN_DURATION_TICKS) { ->

            switchAnnouncement()
        }
    }

    fun playerSwitcher() {
        val shuffledPlayers = players().shuffled()
        val playerCount = shuffledPlayers.count()
        val positionRotations = shuffledPlayers.map { player ->
            PositionRotation(player.x, player.y, player.z, player.yaw, player.pitch)
        }

        for (p in (0 ..< playerCount)) {
            val previousIndex = floorMod(p-1, playerCount)
            shuffledPlayers[p].teleport(positionRotations[previousIndex])
            translate("game.ap2.killeporter.switch_message", shuffledPlayers[previousIndex].nameForScoreboard)
                .formatted(Formatting.GREEN)
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
            UseItemCallback { player: PlayerEntity, _: World, hand: Hand ->
                if (player !is ServerPlayerEntity) return@UseItemCallback ActionResult.PASS

                val stack = player.getStackInHand(hand)

                if (itemUseAllowed || kitHandler!!.isKitSelector(stack)) {
                    return@UseItemCallback ActionResult.PASS
                }

                if (stack.contains(DataComponentTypes.USE_COOLDOWN)) {
                    player.itemCooldownManager.set(stack, 0)
                }

                PlayerUtils.syncPlayerItems(player)
                ActionResult.FAIL
            })

        kitHandler?.setup()
    }
}
