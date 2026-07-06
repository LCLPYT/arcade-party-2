package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.HookContainer
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.KibuScheduling
import work.lclpnet.kibu.scheduler.api.Scheduler
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import kotlin.time.Duration

interface TaskEnv {

    val players: Participants

    val hooks: HookRegistrar

    val scheduler: TaskScheduler

    val translations: Translations

    val level: ServerLevel

    /**
     * The world spawn position all players are teleported to at the start of each task.
     * Used as a reference point for tasks that place things relative to spawn.
     */
    val spawnPos: BlockPos

    fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit)

    fun complete(data: DataContainer<ServerPlayer, PlayerRef>)
}

class TaskEnvImpl(
    override val players: Participants,
    override val level: ServerLevel,
    override val translations: Translations,
    val logger: Logger,
    val createTimer: (String, Duration) -> BossBarTimer,
    val onComplete: (DataContainer<ServerPlayer, PlayerRef>, TaskEnvImpl) -> Unit,
) : TaskEnv {

    override val hooks = HookContainer()
    override val scheduler = Scheduler(logger)
    override val spawnPos: BlockPos = level.respawnData.pos()

    private val timers = ArrayList<BossBarTimer>()
    private var completed = false

    fun init() {
        KibuScheduling.getRootScheduler().addChild(scheduler)
    }

    override fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit) {
        val timer = createTimer(labelKey, duration)
        timers.add(timer)

        timer.whenDone {
            onEnd()
        }
    }

    override fun complete(data: DataContainer<ServerPlayer, PlayerRef>) {
        if (completed) return
        completed = true

        onComplete(data, this)
    }

    fun unload() {
        timers.forEach { it.stop() }
        timers.clear()
        hooks.unload()
        KibuScheduling.getRootScheduler().removeChild(scheduler)
    }
}

interface Task {

    val id: String

    fun begin(env: TaskEnv)

    fun end(env: TaskEnv) {}
}