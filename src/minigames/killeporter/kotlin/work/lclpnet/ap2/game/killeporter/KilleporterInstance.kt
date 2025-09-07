package work.lclpnet.ap2.game.killeporter

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluids
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.world.GameRules
import net.minecraft.world.World
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
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

val MIN_DURATION_TICKS = Ticks.seconds(18)
val MAX_DURATION_TICKS = Ticks.seconds(32)
val GAME_DURATION_TICKS = Ticks.minutes(6)
val TIME_TO_NIGHTFALL_DAYTIME_TICKS = 3600

class KilleporterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    init {
        useSurvivalMode()
    }

    var kitHandler: KitHandler? = null
    var kitLoader: PrefabKitLoader? = null
    var itemUseAllowed = false

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
                entity is ServerPlayerEntity && source.attacker is ServerPlayerEntity && !source.isOf(DamageTypes.PLAYER_EXPLOSION)
            })
        }

        gameHandle.hooks.registerHook(BlockModificationHooks.PLACE_FLUID, BlockModificationHooks.FluidTransferHook { world, pos, entity, fluid ->
            val minDistSq = 6.0 * 6.0
            entity is ServerPlayerEntity && fluid.matchesType(Fluids.LAVA) && players().any {
                it != entity && it.squaredDistanceTo(pos.toCenterPos()) < minDistSq
            }
        })

        switchTimeout()

        timeout(GAME_DURATION_TICKS) { ->
            winManager.forceWin(players().toSet())
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
