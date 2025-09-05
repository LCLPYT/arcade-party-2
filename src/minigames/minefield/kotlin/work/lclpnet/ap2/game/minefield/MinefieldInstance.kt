package work.lclpnet.ap2.game.minefield

import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting.*
import net.minecraft.util.math.BlockPos
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.kibu.hook.world.PressurePlateCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val END_TIME_SECONDS = 15

class MinefieldInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle) {
    
    private val data = OrderedDataContainer(PlayerRef::create)

    val inGoal = mutableSetOf<UUID>()
    var gameEnd = -1
    var taskBar: TranslatedBossBar? = null
    var spawnShape: BlockShape? = null
    var goalShape: BlockShape? = null
    
    override fun getData() = data

    override fun prepare() {
        spawnShape = readShape("spawn-shape")

        for (player in players()) {
            player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()))
        }

        taskBar = useTaskDisplay()
    }

    override fun ready() {
        world.setBlocks(readShape("spawn-gate"), Blocks.AIR)

        goalShape = readShape("goal-shape")

        interval(1) {
            for (player in players()) {
                if (goalShape!!.contains(player.pos)) {
                    onReachGoal(player)
                }
            }
        }

        gameHandle.hooks.registerHook(PressurePlateCallback.HOOK, PressurePlateCallback { _, pos, entity ->
            if (entity is ServerPlayerEntity && players().isParticipating(entity)) {
                onStepOnMine(entity, pos)
            }

            return@PressurePlateCallback true
        })
    }

    fun onReachGoal(player: ServerPlayerEntity) {
        if (!inGoal.add(player.uuid)) return

        Fireworks.spawnGoalFirework(player)

        if (inGoal.size >= gameHandle.getParticipants().count()) {
            winManager.complete()
            return
        }

        if (gameEnd == -1) {
            translate(
                "game.ap2.mine_field.goal",
                styled(player.nameForScoreboard, YELLOW),
                styled(END_TIME_SECONDS, YELLOW)
            ).formatted(GREEN).sendTo(allPlayers())

            gameEnd = Ticks.seconds(END_TIME_SECONDS)

            commons().addTimer(taskBar, END_TIME_SECONDS).then {
                gradePlayers()
                winManager.complete()
            }
        }
    }

    fun gradePlayers() {
        class Grade(val player: ServerPlayerEntity, val distance: Double)

        players().stream()
            .filter { !inGoal.contains(it.uuid) }
            .map { Grade(it, sqrt(goalShape!!.bounds().squaredDistanceTo(it.pos))) }
            .sorted(Comparator.comparingDouble { it.distance })
            .forEachOrdered {
                val detail = translate("ap2.score.blocks_away", LocalizedFormat.format("%.1f", it.distance))
                data.add(it.player, detail)
            }
    }

    fun MinefieldInstance.onStepOnMine(player: ServerPlayerEntity, pos: BlockPos) {
        if (winManager.isGameOver) return

        world.setBlock(pos, Blocks.AIR)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0)
        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.5f, 1.2f)
        player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()))

        translate("game.ap2.mine_field.stepped_on_mine").formatted(RED).sendTo(player, true)
    }
}
