package work.lclpnet.ap2.task_rush.task

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.game.util.createTimer
import kotlin.time.Duration.Companion.seconds

private val NEXT_TASK_DELAY = 4.seconds
private const val TARGET_ROUNDS = 5

class TaskManager(
    val gameHandle: MiniGameHandle,
    val data: IntScoreDataContainer<ServerPlayer, PlayerRef>,
    val onComplete: () -> Unit,
) {

    private val allTasks = setOf(
        HeightTask("highest_pos", Ordering.DESCENDING),
        HeightTask("lowest_pos", Ordering.ASCENDING),
        MobKillTask,
    )
    private val taskQueue = ArrayDeque<Task>()
    private val announcer = Announcer(gameHandle.translations, gameHandle.server)

    private var currentTaskEnv: TaskEnvImpl? = null
    private var currentTask: Task? = null
    private var round = 0

    fun nextTask() {
        val task = pickNextTask()

        startTask(task)
    }

    fun pickNextTask(): Task {
        if (taskQueue.isEmpty()) {
            taskQueue.addAll(allTasks)
            taskQueue.shuffle()

            check(taskQueue.isNotEmpty()) { "Failed to fill task queue" }
        }

        return taskQueue.removeFirst()
    }

    fun startTask(task: Task) {
        round++

        val env = TaskEnvImpl(
            players = gameHandle.participants,
            logger = gameHandle.logger,
            createTimer = { labelKey, duration ->
                gameHandle.createTimer(gameHandle.translations.translateText(labelKey), duration)
            },
            onComplete = { data, env ->
                // TODO eval data, rank players, give top three points with the data container

                task.end(env)
                deferNextTask()
            }
        )

        env.init()

        this.currentTaskEnv = env
        this.currentTask = task

        val msg = gameHandle.translations.translateText("task.${task.id}")
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)

        announcer.announceInChat(msg)

        task.begin(env)
    }

    fun deferNextTask() {
        unload()

        if (round >= TARGET_ROUNDS) {
            onComplete()
            return
        }

        gameHandle.scheduler.timeout(NEXT_TASK_DELAY.inWholeTicks, ::nextTask)
    }

    fun unload() {
        currentTaskEnv?.let { env ->
            currentTask?.end(env)
            env.unload()
        }

        currentTask = null
        currentTaskEnv = null
    }
}