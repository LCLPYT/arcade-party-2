package work.lclpnet.ap2.task_rush.task

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.gamerules.GameRules
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
import work.lclpnet.ap2.impl.util.world.ChunkPersistence
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.shuffle
import kotlin.time.Duration.Companion.seconds

private val NEXT_TASK_DELAY = 4.seconds
private const val TARGET_ROUNDS = 5

class TaskManager(
    val gameHandle: MiniGameHandle,
    val level: ServerLevel,
    val data: IntScoreDataContainer<ServerPlayer, PlayerRef>,
    val chunkPersistence: ChunkPersistence,
    val onComplete: () -> Unit,
) {

    val itemQueue = ItemQueue(gameHandle.scheduler, gameHandle.server, gameHandle.translations)

    val allTasks = setOf(
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
        DieTask,
    )
    private val taskQueue = ArrayDeque<Task>()
    private val announcer = Announcer(gameHandle.translations, gameHandle.server)

    private var currentTaskEnv: TaskEnvImpl? = null
    private var currentTask: Task? = null
    private var pendingNext: TaskHandle? = null
    private var round = 0

    val duplicateDropsEnabled: Boolean
        get() = currentTaskEnv?.duplicateDrops ?: true

    val pvpDisabled: Boolean
        get() = currentTaskEnv?.pvpDisabled ?: true

    val fallDamageDisabled: Boolean
        get() = currentTaskEnv?.fallDamageDisabled ?: true

    fun init() {
        itemQueue.init()
    }

    fun nextTask(initial: Boolean = false) {
        round++

        val task = pickNextTask()

        startTask(task, initial = initial)
    }

    /**
     * Destroys the current task without scoring and starts the given task in its place (same round).
     */
    fun changeTask(task: Task) {
        startTask(task)
    }

    /**
     * Destroys the current task without scoring and advances to the next task from the queue.
     */
    fun skipCurrentTask() {
        nextTask()
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
        // End the previous task atomically: cancel any pending auto-advance and unload the current
        // task before starting the new one, so exactly one task is ever active.
        unload()

        if (!initial) {
            for (player in gameHandle.participants) {
                gameHandle.worldFacade.teleport(player)
            }
        }

        val env = TaskEnvImpl(
            players = gameHandle.participants,
            level = level,
            translations = gameHandle.translations,
            scoreboardManager = gameHandle.scoreboardManager,
            chunkPersistence = chunkPersistence,
            logger = gameHandle.logger,
            itemQueue = itemQueue,
            createTimer = { labelKey, duration ->
                gameHandle.createTimer(gameHandle.translations.translateText(labelKey), duration)
            },
            onComplete = { taskData, _ ->
                awardPoints(taskData)

                deferNextTask()
            }
        )

        env.init()

        this.currentTaskEnv = env
        this.currentTask = task

        val msg = task.announcement(env)
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

        val heard = HashSet<UUID>()

        for (pair: ObjectIntPair<PlayerRef> in order) {
            val player = gameHandle.server.playerList.getPlayer(pair.left().uuid) ?: continue

            heard.add(player.uuid)

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

        // Players without a score are not part of the ranking, so give them the "no reward" sound too.
        for (player in gameHandle.participants) {
            if (player.uuid in heard) continue

            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.UI, 0.5f, 1f)
        }
    }

    fun deferNextTask() {
        unload()

        if (round >= TARGET_ROUNDS) {
            onComplete()
            return
        }

        pendingNext = gameHandle.scheduler.timeout(NEXT_TASK_DELAY.inWholeTicks, ::nextTask)
    }

    fun unload() {
        pendingNext?.cancel()
        pendingNext = null

        currentTaskEnv?.let { env ->
            currentTask?.end(env)
            env.unload()
        }

        currentTask = null
        currentTaskEnv = null

        restoreDefaults()
    }

    private fun restoreDefaults() {
        level.gameRules.set(GameRules.NATURAL_HEALTH_REGENERATION, true, level.server)
    }
}