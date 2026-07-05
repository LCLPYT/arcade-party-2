package work.lclpnet.ap2.task_rush.task

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
import kotlin.time.Duration

interface TaskEnv {

    val players: Participants

    val hooks: HookRegistrar

    val scheduler: TaskScheduler

    fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit)

    fun complete(data: DataContainer<ServerPlayer, PlayerRef>)
}

class TaskEnvImpl(
    override val players: Participants,
    val logger: Logger,
    val createTimer: (String, Duration) -> BossBarTimer,
    val onComplete: (DataContainer<ServerPlayer, PlayerRef>, TaskEnvImpl) -> Unit,
) : TaskEnv {

    override val hooks = HookContainer()
    override val scheduler = Scheduler(logger)

    fun init() {
        KibuScheduling.getRootScheduler().addChild(scheduler)
    }

    override fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit) {
        createTimer(labelKey, duration).whenDone {
            onEnd()
        }
    }

    override fun complete(data: DataContainer<ServerPlayer, PlayerRef>) {
        onComplete(data, this)
    }

    fun unload() {
        hooks.unload()
        KibuScheduling.getRootScheduler().removeChild(scheduler)
    }
}

interface Task {

    val id: String

    fun begin(env: TaskEnv)

    fun end(env: TaskEnv) {}
}