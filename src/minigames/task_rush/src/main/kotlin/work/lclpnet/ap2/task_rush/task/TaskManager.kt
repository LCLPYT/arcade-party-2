package work.lclpnet.ap2.task_rush.task

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.game.util.ResultAnnouncement
import work.lclpnet.ap2.game.util.createTimer
import kotlin.time.Duration.Companion.seconds

private val NEXT_TASK_DELAY = 4.seconds
private const val TARGET_ROUNDS = 5

class TaskManager(
    val gameHandle: MiniGameHandle,
    val level: ServerLevel,
    val data: IntScoreDataContainer<ServerPlayer, PlayerRef>,
    val onComplete: () -> Unit,
) {

    private val allTasks = setOf(
        HeightTask("highest_pos", Ordering.DESCENDING),
        HeightTask("lowest_pos", Ordering.ASCENDING),
        MobKillTask,
        WaterDistanceTask,
        OreCollectTask,
        FallIntoWaterTask,
        BowDistanceTask,
        LogsCollectTask,
        DiveDeepestTask,
        SeedsCollectTask,
        FoodCollectTask,
        EntityDamageTask,
        BreakBlocksTask,
        FlowerTypesTask,
        DarkestPlaceTask,
        StandInAreaTask,
        TreasureHuntTask,
        FirstOreTask,
        BreedAnimalsTask,
        ReachCoordsTask,
    )
    private val taskQueue = ArrayDeque<Task>()
    private val announcer = Announcer(gameHandle.translations, gameHandle.server)

    private var currentTaskEnv: TaskEnvImpl? = null
    private var currentTask: Task? = null
    private var round = 0

    fun nextTask(initial: Boolean = false) {
        val task = pickNextTask()

        startTask(task, initial = initial)
    }

    fun pickNextTask(): Task {
        if (taskQueue.isEmpty()) {
            taskQueue.addAll(allTasks)
            taskQueue.shuffle()

            check(taskQueue.isNotEmpty()) { "Failed to fill task queue" }
        }

        return taskQueue.removeFirst()
    }

    fun startTask(task: Task, initial: Boolean = false) {
        round++

        if (!initial) {
            for (player in gameHandle.participants) {
                gameHandle.worldFacade.teleport(player)
            }
        }

        val env = TaskEnvImpl(
            players = gameHandle.participants,
            level = level,
            translations = gameHandle.translations,
            logger = gameHandle.logger,
            createTimer = { labelKey, duration ->
                gameHandle.createTimer(gameHandle.translations.translateText(labelKey), duration)
            },
            onComplete = { taskData, env ->
                awardPoints(taskData)

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

    /**
     * Ranks the players by the task's result and awards points to the game-wide score container.
     * Rank 1 earns 3 points, rank 2 earns 2 points and rank 3 earns 1 point. Ties share a rank.
     */
    private fun awardPoints(taskData: DataContainer<ServerPlayer, PlayerRef>) {
        val order = taskData.streamEntriesRanked()
            .flatMap { obj -> obj.stream() }
            .toList()

        val announcement = ResultAnnouncement(
            gameHandle.translations,
            gameHandle.fontService,
            { player -> PlayerRef.create(player) },
            order,
            { ref -> taskData.getEntry(ref) }
        )

        for (player in PlayerLookup.all(gameHandle.server)) {
            announcement.sendTop(3, player, labelKey = "task_results")
        }

        for (pair: ObjectIntPair<PlayerRef> in order) {
            val player = gameHandle.server.playerList.getPlayer(pair.left().uuid) ?: continue

            val rank = pair.rightInt()

            when (rank) {
                1 -> player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 0.5f, 2f)
                2 -> player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 0.5f, 1.6f)
                3 -> player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 0.5f, 1.4f)
                else -> player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.UI, 0.5f, 1f)
            }

            val points = when (rank) {
                1 -> 3
                2 -> 2
                3 -> 1
                else -> 0
            }

            if (points == 0) continue

            data.addScore(player, points)
        }
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