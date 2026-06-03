package work.lclpnet.ap2.game.aim_master

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.round

private const val SCORE_GOAL = 24
private const val TARGET_NUMBER = 6
private const val TARGET_MIN_DISTANCE = 2
private const val SPHERE_RADIUS = 15
private const val SPHERE_OFFSET = 5
private const val UPWARD_TILT = 0.55
private const val ELLIPSE_FACTOR = 0.35
private const val CONE_FOV = 35

private val CLICKS = Stat("clicks", 0)
private val MISSES = Stat("misses", 0)
private val ACCURACY = Stat("accuracy", 0f)
private val STREAK = Stat("streak", 0)

class AimMasterInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrap {

    private val data = IntScoreDataContainer(PlayerRef::create)

    private val stats = createStats(data, CLICKS, MISSES, ACCURACY, STREAK)
    private val currentStreak = HashMap<UUID, Int>()

    private lateinit var bossBar: DynamicTranslatedPlayerBossBar
    private lateinit var manager: AimMasterManager
    private lateinit var sequence: AimMasterSequence

    override fun getData() = data

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        val generator = StackedRoomGenerator(
            world,
            map,
            StackedRoomGenerator.Coordinates.RELATIVE
        ) { _, spawn, yaw, _ ->
            AimMasterDomain(spawn, yaw, world)
        }

        val positionGenerator = PositionGenerator(
            SPHERE_RADIUS,
            SPHERE_OFFSET,
            UPWARD_TILT,
            ELLIPSE_FACTOR,
            BlockPos(0, 0, 0),
            CONE_FOV,
            TARGET_NUMBER,
            TARGET_MIN_DISTANCE
        )

        val blockOptions = BlockOptions()
        val sequenceGenerator = SequenceGenerator(positionGenerator, blockOptions, SCORE_GOAL)

        sequence = sequenceGenerator.sequence

        return generator.generate(gameHandle.participants)
            .thenAccept { result ->
                manager = AimMasterManager(result.rooms(), sequence)
            }
            .exceptionally { throwable ->
                gameHandle.logger.error("Failed to create domains", throwable)
                null
            }
    }

    override fun prepare() {
        for (player in gameHandle.participants) {
            manager.domains[player.uuid]?.teleport(player)
        }
        bossBar = usePlayerDynamicTaskDisplay(styled(SCORE_GOAL, ChatFormatting.YELLOW))
        bossBar.setPercent(0f)
    }

    override fun go() {
        val sequenceItems = sequence.items

        for (player in gameHandle.participants) {
            val domain = manager.domains[player.uuid] ?: continue
            domain.teleport(player)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
            domain.setBlocks(sequenceItems.first(), player)
        }

        val hooks = gameHandle.hooks
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks) { player, slot ->
            if (slot != 4) PlayerInventoryAccess.setSelectedSlot(player, 4)
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, _ -> invokeRayCaster(player) }
        PlayerSwingHandHook.HOOK.registerWith(hooks) { player, _ -> invokeRayCaster(player) }
    }

    private fun invokeRayCaster(player: Player): InteractionResult {
        if (winManager.isGameOver) return InteractionResult.FAIL

        val domain = manager.domains[player.uuid] ?: return InteractionResult.FAIL
        val serverPlayer = player as ServerPlayer

        val clicks = stats.increment(serverPlayer, CLICKS)

        if (domain.rayCaster(serverPlayer, SPHERE_RADIUS)) {
            data.addScore(serverPlayer, 1)
            val newScore = data.getScore(serverPlayer)
            bossBar.getBossBar(serverPlayer).progress = newScore.toFloat() / SCORE_GOAL

            val streak = (currentStreak[player.uuid] ?: 0) + 1
            currentStreak[player.uuid] = streak
            stats.modify(serverPlayer, STREAK) { maxOf(it, streak) }
            updateAccuracy(serverPlayer, newScore, clicks)

            val target = domain.currentTarget
            val serverWorld = serverPlayer.level()

            if (target != null) {
                serverWorld.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x.toDouble(), target.y.toDouble(), target.z.toDouble(), 12, 0.4, 0.4, 0.4, 0.01)
            }
            ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5f, 0.8f)

            if (newScore >= SCORE_GOAL) win(serverPlayer)
            else manager.advancePlayer(serverPlayer)

            return InteractionResult.FAIL
        }

        currentStreak[player.uuid] = 0
        stats.increment(serverPlayer, MISSES)
        updateAccuracy(serverPlayer, data.getScore(serverPlayer), clicks)

        ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.3f, 0.2f)
        return InteractionResult.PASS
    }

    private fun updateAccuracy(player: ServerPlayer, hits: Int, clicks: Int) {
        val accuracy = if (clicks == 0) 0f else round(hits * 1000f / clicks) / 10f
        stats.set(player, ACCURACY, accuracy)
    }

    private fun win(winner: ServerPlayer) {
        val domain = manager.domains[winner.uuid] ?: return
        domain.removeBlocks(sequence.items.last())

        val task = WinAnimationTask(winner, domain, sequence)
        val taskHandle = gameHandle.rootScheduler.interval(task, 5)
        winManager.complete().then(taskHandle::cancel)
    }
}

private class WinAnimationTask(
    private val player: ServerPlayer,
    private val domain: AimMasterDomain,
    private val sequence: AimMasterSequence
) : SchedulerAction {
    var time = 0

    override fun run(info: RunningTask) {
        val i = time / 2
        val sequenceItem = sequence.items[sequence.items.size - 1 - i]

        if (time % 2 == 0) domain.setBlocks(sequenceItem, player)
        else domain.removeBlocks(sequenceItem)

        if (i >= sequence.items.size - 1) info.cancel()
        time++
    }
}
