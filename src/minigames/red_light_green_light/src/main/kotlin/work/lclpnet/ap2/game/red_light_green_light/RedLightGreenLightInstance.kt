package work.lclpnet.ap2.game.red_light_green_light

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Team
import org.json.JSONArray
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.movement.MovementListener
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.game.util.RayCaster
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val UNTIL_STOP_MIN_TICKS = 70
private const val UNTIL_STOP_MAX_TICKS = 110
private const val WARN_TIME_MIN_TICKS = 35
private const val WARN_TIME_MAX_TICKS = 70
private const val FROZEN_MIN_TICKS = 60
private const val FROZEN_MAX_TICKS = 105
private const val END_TIME_SECONDS = 15
private const val CLOSEST_STOP_SENTINEL = UNTIL_STOP_MAX_TICKS / 20f

private data class Grade(val player: ServerPlayer, val distance: Double)

class RedLightGreenLightInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    private val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler)
    override val data = OrderedDataContainer(PlayerRef::create)
    private val random = Random()
    private val inGoal = HashSet<UUID>()
    private val moved = HashSet<UUID>()
    private val trafficLights = ArrayList<TrafficLight>()
    private val movementDetector = RLGLMovementDetector()
    private val stats = createStats(Resets, YellowMovingTime, ClosestStopTime, DistanceReset, AvgYellowTimeUsage)
    private val rlglStats = RedLightGreenLightStats(stats)
    private val lastMovingTick = HashMap<UUID, Int>()
    private val pendingStopTime = HashMap<UUID, Float>()
    private var gameTick = 0
    private lateinit var tracker: MovementTracker
    private lateinit var taskBar: TranslatedBossBar
    private lateinit var goal: BlockBox
    private var timer = 0
    private var warn = 0
    private var go = 0
    private var gameEnd = -1

    override fun prepare() {
        taskBar = useTaskDisplay()

        val map = map
        val world = level

        goal = MapUtil.readBox(map.requireProperty("goal"))
        tracker = MovementTracker(goal)

        val spawnArea = MapUtil.readBox(map.requireProperty("spawn-area"))
        val yaw = MapUtils.getSpawnYaw(map)

        for (participant in gameHandle.participants) {
            val pos = spawnArea.randomPos(random)
            participant.teleportTo(world, pos.x(), pos.y(), pos.z(), emptySet(), yaw, 0f, true)
        }

        readTrafficLights()
        setTrafficLightStatus(EnumSet.noneOf(TrafficLight.Status::class.java))

        movementBlocker.init(gameHandle.hooks)

        val scoreboardManager: CustomScoreboardManager = gameHandle.scoreboardManager
        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        scoreboardManager.joinTeam(gameHandle.participants, team)
    }

    override fun go() {
        val hooks = gameHandle.hooks

        movementDetector.register(::onMovedWhileRed)
        movementDetector.init(hooks)

        PlayerMoveCallback.HOOK.registerWith(hooks) { player, _, _ ->
            onMove(player)
            false
        }

        for (player in gameHandle.participants) {
            stats.set(player, ClosestStopTime, CLOSEST_STOP_SENTINEL)
        }

        openGate()
        scheduleNextStop()
        setStatus(TrafficLight.Status.GREEN)

        runEveryTick {
            tick()
        }
    }

    private fun readTrafficLights() {
        val json = map.getProperty<JSONArray?>("traffic-lights") ?: return

        trafficLights.clear()

        for (obj in json) {
            if (obj !is org.json.JSONObject) continue
            trafficLights.add(trafficLightFromJson(obj))
        }
    }

    private fun setStatus(status: TrafficLight.Status) {
        if (status == TrafficLight.Status.GREEN) {
            movementDetector.unfixAll()

            for (uuid in moved) {
                val player = gameHandle.server.playerList.getPlayer(uuid) ?: continue
                movementBlocker.enableMovement(player)
            }

            moved.clear()
        } else if (status == TrafficLight.Status.RED) {
            for (player in gameHandle.participants) {
                if (inGoal.contains(player.uuid)) continue
                movementDetector.fixPosition(player)
            }
        }

        setTrafficLightStatus(EnumSet.of(status))

        taskBar.setColor(when (status) {
            TrafficLight.Status.RED -> BossEvent.BossBarColor.RED
            TrafficLight.Status.YELLOW -> BossEvent.BossBarColor.YELLOW
            TrafficLight.Status.GREEN -> BossEvent.BossBarColor.GREEN
        })

        val key = when (status) {
            TrafficLight.Status.RED -> "game.ap2.red_light_green_light.stop"
            TrafficLight.Status.YELLOW -> "game.ap2.red_light_green_light.warn"
            TrafficLight.Status.GREEN -> "game.ap2.red_light_green_light.go"
        }

        val msg = gameHandle.translations.translateText(key).formatted(ChatFormatting.BOLD, when (status) {
            TrafficLight.Status.RED -> ChatFormatting.RED
            TrafficLight.Status.YELLOW -> ChatFormatting.YELLOW
            TrafficLight.Status.GREEN -> ChatFormatting.GREEN
        })

        val world = level

        for (player in PlayerLookup.level(world)) {
            when (status) {
                TrafficLight.Status.RED -> player.playNotifySound(SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 1f, 0.5f)
                TrafficLight.Status.YELLOW -> player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 0.5f)
                TrafficLight.Status.GREEN -> player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 1f, 1f)
            }

            Title.get(player).title(msg.translateFor(player))
        }
    }

    private fun setTrafficLightStatus(status: EnumSet<TrafficLight.Status>) {
        val world = level

        for (light in trafficLights) {
            light.set(status, world)
        }
    }

    private fun openGate() {
        val gate = MapUtil.readBox(map.requireProperty("gate"))
        val world = level
        val air = Blocks.AIR.defaultBlockState()

        for (pos in gate) {
            world.setBlockAndUpdate(pos, air)
        }
    }

    private fun onMove(player: ServerPlayer) {
        if (timer <= 0 || inGoal.contains(player.uuid) || !gameHandle.participants.isParticipating(player)) return

        tracker.track(player)

        if (goal.contains(player.position())) {
            onGoalReached(player)
        }
    }

    private fun onMovedWhileRed(player: ServerPlayer) {
        if (winManager.isGameOver
            || !gameHandle.participants.isParticipating(player)
            || inGoal.contains(player.uuid)
            || !moved.add(player.uuid)) return

        rlglStats.reset(player)

        movementDetector.unfixPosition(player)
        punish(player)
    }

    private fun punish(player: ServerPlayer) {
        val world = level

        val x = player.x; val y = player.y; val z = player.z
        world.sendParticles(ParticleTypes.CRIT, x, y, z, 100, 0.1, 0.1, 0.1, 1.0)

        val pos = tracker.getMostDistantPos(player)

        if (pos != null) {
            world.playSound(player, x, y, z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f)

            val nx = pos.x
            val ny = findSuitableY(world, pos)
            val nz = pos.z

            val dx = nx - x
            val dz = nz - z
            rlglStats.recordResetDistance(player, sqrt(dx * dx + dz * dz))

            player.teleportTo(world, nx, ny, nz, emptySet(), player.yRot, player.xRot, true)
            player.playNotifySound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f)
        } else {
            world.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f)
        }

        movementBlocker.disableMovement(player)

        translate("game.ap2.red_light_green_light.moved")
            .formatted(ChatFormatting.RED)
            .sendTo(player)
    }

    private fun findSuitableY(world: ServerLevel, pos: Vec3): Double {
        val ctx = RayCaster.GenericRaycastContext(pos, pos.subtract(0.0, 10.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE)
        val hit = RayCaster.rayCastBlockCollision(world, ctx)
        return (hit.location.y * 20).roundToInt() / 20.0
    }

    private fun onGoalReached(player: ServerPlayer) {
        if (!inGoal.add(player.uuid)) return

        data.add(player)

        val world = level

        Fireworks.spawnGoalFirework(player)

        if (inGoal.size >= gameHandle.participants.count()) {
            winManager.complete()
        } else if (gameEnd == -1) {
            translate(
                "game.ap2.red_light_green_light.goal",
                styled(player.scoreboardName, ChatFormatting.YELLOW),
                styled(END_TIME_SECONDS, ChatFormatting.YELLOW)
            )
                .formatted(ChatFormatting.GREEN)
                .sendTo(PlayerLookup.level(world))

            gameEnd = Ticks.seconds(END_TIME_SECONDS)
        }
    }

    private fun scheduleNextStop() {
        timer = UNTIL_STOP_MIN_TICKS + random.nextInt(UNTIL_STOP_MAX_TICKS - UNTIL_STOP_MIN_TICKS + 1)

        val randomNextWarn = WARN_TIME_MIN_TICKS + random.nextInt(WARN_TIME_MAX_TICKS - WARN_TIME_MIN_TICKS + 1)
        warn = randomNextWarn.coerceIn(1, maxOf(1, timer - WARN_TIME_MIN_TICKS))

        go = FROZEN_MIN_TICKS + random.nextInt(FROZEN_MAX_TICKS - FROZEN_MIN_TICKS + 1)
    }

    fun tick() {
        gameTick++

        if (gameEnd >= 0) {
            val ticksUntilEnd = gameEnd--

            if (ticksUntilEnd % 20 == 0) {
                taskBar.setProgress(ticksUntilEnd / 20f / END_TIME_SECONDS)
            }

            if (ticksUntilEnd == 0) {
                gradePlayers()
                winManager.complete()
                return
            }
        }

        val relTime = timer--

        if (relTime < 0) {
            if (relTime == -go) {
                scheduleNextStop()
                commitClosestStops()
                setStatus(TrafficLight.Status.GREEN)
            }
            return
        }

        if (relTime == 0) {
            captureClosestStops()
            recordYellowUsage()
            setStatus(TrafficLight.Status.RED)
            return
        }

        if (relTime == warn) {
            setStatus(TrafficLight.Status.YELLOW)
        }

        trackMovement(yellow = relTime <= warn)
    }

    private fun trackMovement(yellow: Boolean) {
        for (player in gameHandle.participants) {
            if (inGoal.contains(player.uuid)) continue
            if (!MovementListener.isMovementInput(player.lastClientInput)) continue

            lastMovingTick[player.uuid] = gameTick

            if (yellow) {
                rlglStats.movedOnYellow(player)
            }
        }
    }

    private fun recordYellowUsage() {
        for (player in gameHandle.participants) {
            if (inGoal.contains(player.uuid)) continue
            rlglStats.endYellowPhase(player, warn)
        }
    }

    private fun captureClosestStops() {
        pendingStopTime.clear()

        for (player in gameHandle.participants) {
            if (inGoal.contains(player.uuid)) continue

            val last = lastMovingTick[player.uuid] ?: continue
            pendingStopTime[player.uuid] = (gameTick - last) / 20f
        }
    }

    private fun commitClosestStops() {
        for ((uuid, seconds) in pendingStopTime) {
            if (moved.contains(uuid)) continue

            val player = gameHandle.server.playerList.getPlayer(uuid) ?: continue
            if (!gameHandle.participants.isParticipating(player)) continue

            rlglStats.recordStopTime(player, seconds)
        }

        pendingStopTime.clear()
    }

    private fun gradePlayers() {
        gameHandle.participants
            .filter { player -> !inGoal.contains(player.uuid) }
            .map { player ->
                val distanceSq = goal.squaredDistanceTo(player.position())
                Grade(player, sqrt(distanceSq))
            }
            .sortedBy { it.distance }
            .forEach { grade ->
                data.add(grade.player, translate(
                    "ap2.score.blocks_away",
                    LocalizedFormat.format("%.1f", grade.distance)
                ))
            }
    }
}
