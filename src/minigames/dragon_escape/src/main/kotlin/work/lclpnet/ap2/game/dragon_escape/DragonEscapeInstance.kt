package work.lclpnet.ap2.game.dragon_escape

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.core.mixin.entity.LivingEntityAccessor
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.CombinedDataContainer
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.OrderedDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.dragon_escape.kit.EnderPearlKit
import work.lclpnet.ap2.game.dragon_escape.kit.LeapKit
import work.lclpnet.ap2.game.dragon_escape.kit.WindChargeKit
import work.lclpnet.ap2.game.kit.KitHandler
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.ChunkPersistence
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.math.SplinePath
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.access.misc.DamageTrackerAccess
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.util.OnGroundDetector
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.floor
import kotlin.math.max

private const val DEBUG_PATH = false
private const val DEBUG_PROGRESS = false

private fun milliTime() = System.nanoTime() / 1_000_000L

private class Tracker(var anchor: Vec3) {
    var maxProgress: Double = 0.0
}

class DragonEscapeInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    private val completed = OrderedDataContainer(PlayerRef::create)
    private val score = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "ap2.score.distance")
    override val data = useDataContainer { CombinedDataContainer(listOf(completed, score)) }
    private val random = Random()
    private val inGoal = HashSet<UUID>()
    private val trackers = HashMap<UUID, Tracker>()
    private val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val announcer = useAnnouncer()
    private var startMs = 0L
    private lateinit var goalShape: BlockShape
    private lateinit var path: SplinePath
    private lateinit var dragonController: DragonController
    private lateinit var pseudoElimination: PseudoElimination
    private var playerStartProgress = 0.0
    private var playerPathLength = 0.0
    private var pathEliminationDistance = 30.0
    private var maxScore = Double.NEGATIVE_INFINITY
    private var checkForCompletion = false
    private var itemUseAllowed = false
    private lateinit var kitHandler: KitHandler
    private lateinit var progressObjective: Objective

    init {
        useOldCombat()
    }

    override fun prepare() {
        if (!readProps()) return

        pseudoElimination = PseudoElimination(gameHandle, level)

        markChunksPersistent()
        setupDragon()
        setupTrackers()
        blockMovement()
        setupScoreboard()

        val visibilityHandler = commons().addVisibilityChanger(commons().noCollision())

        setupKits(visibilityHandler)

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, false)
            .set(GameRules.SPAWN_MOBS, false)

        if (DEBUG_PATH) {
            debugPath()
        }
    }

    private fun readProps(): Boolean {
        val props = map.properties
        val keypointsJson = props.getJSONArray("dragon-path")
        val logger = gameHandle.logger

        val path = MapUtil.readSplinePath(keypointsJson, logger).orElse(null) ?: run {
            logger.error("Failed to create dragon path, aborting game...")
            gameHandle.complete(MiniGameResults.EMPTY)
            return false
        }

        goalShape = MapUtil.readShape(props.getJSONObject("goal-shape"))

        val playerStartPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-start"))
        playerStartProgress = path.getProgress(playerStartPos)

        val playerEndPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-end"))
        val playerEndProgress = path.getProgress(playerEndPos)

        playerPathLength = (playerEndProgress - playerStartProgress) * path.length

        pathEliminationDistance = props.optDouble("path-elimination-distance", pathEliminationDistance)

        this.path = path

        return true
    }

    private fun setupDragon() {
        dragonController = DragonController(
            path, level, random,
            { pos -> !goalShape.contains(pos) },
            { pseudoElimination.iterateParticipants().iterator() }
        )

        dragonController.spawnDragon()
        dragonController.init(gameHandle.scheduler)
    }

    private fun setupTrackers() {
        for (player in gameHandle.participants) {
            val anchor = path.getNearestPosition(player.position())
            trackers[player.uuid] = Tracker(anchor)
        }
    }

    private fun setupKits(visibilityHandler: VisibilityHandler) {
        kitHandler = KitHandler.create(gameHandle, level) { kitHandle ->
            listOf(
                LeapKit(kitHandle),
                EnderPearlKit(kitHandle, path),
                WindChargeKit(kitHandle)
            )
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(gameHandle.hooks) { _player, _, hand ->
            val player = _player as? ServerPlayer ?: return@registerWith InteractionResult.PASS

            val stack = player.getItemInHand(hand)

            if (itemUseAllowed || kitHandler.isKitSelector(stack) || visibilityHandler.isVisibilityChanger(stack)) {
                return@registerWith InteractionResult.PASS
            }

            if (stack.has(DataComponents.USE_COOLDOWN)) {
                player.cooldowns.addCooldown(stack, 0)
            }

            PlayerUtils.syncPlayerItems(player)

            InteractionResult.FAIL
        }

        kitHandler.setup()
    }

    private fun markChunksPersistent() {
        val persistence = ChunkPersistence(level, gameHandle)
        val samples = 1000

        for (i in 0 until samples) {
            val t = i.toDouble() / (samples - 1)
            val pos = path.samplePosition(t)

            val cx = SectionPos.posToSectionCoord(pos.x())
            val cz = SectionPos.posToSectionCoord(pos.z())

            persistence.markPersistent(cx, cz)
        }
    }

    private fun debugPath() {
        val debugger = SplinePathDebugger(commons().debugController(), path)
        debugger.renderPath(1000)

        if (!DEBUG_PROGRESS) return

        debugger.renderLiveProgress({
            val players = pseudoElimination.streamParticipants().toList()
            val d = dragonController.dragon()
            if (d != null) players + d else players
        }, gameHandle.scheduler)
    }

    override fun teleportPlayers() {
        val shapeJson = map.properties.getJSONObject("spawn-shape")
        val spawnShape = MapUtil.readShape(shapeJson)

        val spawnPool = mutableListOf<BlockPos>()

        for (pos in spawnShape) {
            spawnPool.add(pos.immutable())
        }

        if (spawnPool.isEmpty()) {
            gameHandle.logger.error("Spawn shape is empty")
            return
        }

        val spawns = spawnPool.toMutableList()
        val world = level
        val yaw = MapUtils.getSpawnYaw(map)

        for (player in gameHandle.participants) {
            if (spawns.isEmpty()) {
                spawns.addAll(spawnPool)
            }

            val pos = spawns.removeAt(random.nextInt(spawns.size))

            player.teleportTo(
                world,
                pos.x + 0.5,
                pos.y.toDouble(),
                pos.z + 0.5,
                emptySet(),
                yaw,
                0f,
                true
            )
        }
    }

    private fun blockMovement() {
        movementBlocker.init(gameHandle.hooks)

        for (player in gameHandle.participants) {
            movementBlocker.disableMovement(player)
        }
    }

    private fun unblockMovement() {
        for (player in gameHandle.participants) {
            movementBlocker.enableMovement(player)
        }
    }

    private fun setupScoreboard() {
        val scoreboardManager = gameHandle.scoreboardManager

        progressObjective = scoreboardManager.createObjective(
            "progress", ObjectiveCriteria.DUMMY,
            Component.literal("Progress").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            ObjectiveCriteria.RenderType.INTEGER,
            StyledFormat.PLAYER_LIST_DEFAULT
        )

        for (player in pseudoElimination.iterateParticipants()) {
            updatePlayerProgress(player)
        }

        scoreboardManager.setDisplay(DisplaySlot.LIST, progressObjective)
    }

    override fun configureStartup(sequence: GameStartSequence) {
        val delay = KitHandler.DEFAULT_TIMER_DURATION
        kitHandler.startKitSelectionTimer(this, delay + sequence.initialDelay)
        sequence.extraDelay += delay

        super.configureStartup(sequence)
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                if (entity !is ServerPlayer
                    || !gameHandle.participants.isParticipating(entity)
                    || inGoal.contains(entity.uuid)
                    || source.entity is ServerPlayer) {
                    return@allow false
                }

                if (source.entity is EnderDragon) {
                    softEliminateAndCheck(entity)
                    return@allow false
                }

                !source.`is`(DamageTypes.FIREWORKS)
            }

            ProtectionTypes.EXPLOSION.allow(config) { arg -> arg.directSourceEntity is WindCharge }
        }

        kitHandler.disableKitChanger()
        kitHandler.selectKitItem()

        setupSmoothDeath()
        unblockMovement()

        dragonController.startMoving(gameHandle.scheduler)

        gameHandle.scheduler.interval(::tick, 1)

        startMs = milliTime()
        itemUseAllowed = true
    }

    private fun setupSmoothDeath() {
        EntityHealthCallback.HOOK.registerWith(gameHandle.hooks) { entity, health ->
            if (entity !is ServerPlayer || health > 0) return@registerWith false

            val recentDamage = DamageTrackerAccess.getRecentDamage(entity)
            val size = recentDamage.size

            if (size == 0) {
                softEliminateAndCheck(entity)
            } else {
                val damageRecord = recentDamage[size - 1]
                val source = damageRecord.source()

                if ((entity as LivingEntityAccessor).invokeCheckTotemDeathProtection(source)) {
                    return@registerWith true
                }

                softEliminateAndCheck(entity)
            }

            true
        }
    }

    @Synchronized
    private fun tick() {
        if (winManager.gameOver) return

        var check = checkForCompletion

        for (player in pseudoElimination.iterateParticipants()) {
            if (goalShape.contains(player.position()) && !inGoal.contains(player.uuid) && OnGroundDetector.isOnGroundServer(player)) {
                onReachGoal(player)
                check = true
                continue
            }

            val progress = getProgress(player)

            updateTracker(player, progress)
            updatePlayerProgress(player)

            if ((pseudoElimination.isParticipating(player) && progress <= dragonController.dragonProgress)
                || !player.position().closerThan(path.samplePosition(progress), pathEliminationDistance)) {

                softEliminate(player)
                check = true
            }

            player.remainingFireTicks = 0
        }

        if (check) {
            checkComplete()
        }
    }

    @Synchronized
    private fun updateTracker(player: ServerPlayer, progress: Double) {
        val tracker = trackers[player.uuid]

        if (tracker == null || progress <= tracker.maxProgress || !OnGroundDetector.isOnGroundServer(player)) return

        tracker.maxProgress = progress
        tracker.anchor = path.samplePosition(progress)
    }

    private fun onReachGoal(player: ServerPlayer) {
        if (!inGoal.add(player.uuid) || winManager.gameOver) return

        val time = (milliTime() - startMs) / 1000.0
        val duration = TimeHelper.formatTime(gameHandle.translations, time, "%02d", "%06.3f", "%.3f")

        completed.add(player, duration)

        gameHandle.translations.translateText(
            "goal",
            styled(player.scoreboardName, ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GREEN)
            .sendTo(PlayerLookup.all(gameHandle.server))

        Fireworks.spawnGoalFirework(player)

        val tracker = trackers[player.uuid] ?: return

        tracker.maxProgress = 1.0
        updatePlayerProgress(player)
    }

    @Synchronized
    private fun softEliminateAndCheck(player: ServerPlayer) {
        softEliminate(player)
        checkComplete()
    }

    @Synchronized
    private fun softEliminate(player: ServerPlayer) {
        if (pseudoElimination.eliminate(player) && !winManager.gameOver) {
            trackScore(player)
        }

        val tracker = trackers[player.uuid] ?: return

        val pos = tracker.anchor
        val dir = path.sampleDirection(tracker.maxProgress).normalize()

        player.teleportTo(
            level,
            pos.x,
            pos.y,
            pos.z,
            emptySet(),
            MathUtil.yaw(dir),
            MathUtil.pitch(dir),
            true
        )
    }

    @Synchronized
    private fun trackScore(player: ServerPlayer) {
        val distance = getDistance(player)

        if (distance > maxScore) {
            maxScore = distance
        }

        score.setScore(player, distance)
    }

    private fun getProgress(player: ServerPlayer): Double {
        return path.getProgress(player.position())
    }

    @Synchronized
    private fun getDistance(player: ServerPlayer): Double {
        val tracker = trackers[player.uuid]
        val progress = tracker?.maxProgress ?: getProgress(player)

        return max(0.0, (progress - playerStartProgress) * path.length)
    }

    private fun updatePlayerProgress(player: ServerPlayer) {
        val tracker = trackers[player.uuid] ?: return

        val progress = getPlayerProgress(tracker.maxProgress)
        val percent = floor(progress * 100).toInt()

        val format = FixedFormat(Component.literal("$percent%").withStyle(ChatFormatting.YELLOW))

        gameHandle.scoreboardManager.setNumberFormat(player, progressObjective, format)
    }

    private fun getPlayerProgress(progress: Double): Double {
        val corrected = progress - playerStartProgress

        return (corrected * path.length / playerPathLength).coerceIn(0.0, 1.0)
    }

    @Synchronized
    private fun checkComplete() {
        if (winManager.gameOver) return

        if (inGoal.size >= 3) {
            complete()
            return
        }

        val remaining = streamRemaining().toList()

        if (remaining.size >= 2) return

        if (remaining.size == 1) {
            val last = remaining[0]
            val distance = getDistance(last)

            if (distance > maxScore) {
                trackScore(last)
                complete()
                return
            }

            checkForCompletion = true
            return
        }

        complete()
    }

    private fun streamRemaining() = pseudoElimination.streamParticipants()
        .filter { !inGoal.contains(it.uuid) }

    @Synchronized
    private fun complete() {
        if (winManager.gameOver) return

        streamRemaining().forEach(::trackScore)

        winManager.complete()
    }
}
