package work.lclpnet.ap2.game.pig_race

import net.minecraft.ChatFormatting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.monster.Strider
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.music.WeightedSong
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.pig_race.util.PRProgress
import work.lclpnet.ap2.game.pig_race.util.PRScoreboard
import work.lclpnet.ap2.game.pig_race.util.createSegmentedPath
import work.lclpnet.ap2.game.util.usePlayerDynamicDisplay
import work.lclpnet.ap2.game.util.usePlayerDynamicTaskDisplay
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.Ordering
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.music.MusicHelper
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.heads.PlayerHeads
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.CollisionDetector
import work.lclpnet.gaco.collisions.movement.TickMovementObserver
import work.lclpnet.gaco.ds.Checkpoint
import work.lclpnet.gaco.math.SplinePath
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import work.lclpnet.kibu.hook.entity.EntityMountCallback
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.hook.player.PlayerTeleportedCallback
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.max

const val NEXT_ROUND_SONG_ID = "ap_begin"
const val STICK_SLOT = 4
private const val CATCHUP_MIN_DISTANCE = 10.0
private const val CATCHUP_MAX_DISTANCE = 75.0
private const val MAX_CATCHUP_BOOST = 0.4

private enum class Variant { PIG, STRIDER }

class PigRaceInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val mapSchema: PigRaceSchema,
    private val nextRoundSong: WeightedSong?,
) : FFAGameInstance(gameHandle, level, map) {

    private val winnerData = OrderedDataContainer(PlayerRef::create)
    private val distanceData = DoubleScoreDataContainer(
        PlayerRef::create,
        Ordering.ASCENDING,
        "ap2.score.blocks_away"
    )
    override val data = CombinedDataContainer(listOf(winnerData, distanceData))
    private val random = Random()
    private val collisionDetector: CollisionDetector = ChunkedCollisionDetector()
    private val movementObserver = TickMovementObserver(collisionDetector, gameHandle.participants::isParticipating)
    private val pendingEntities = HashMap<UUID, PendingEntity<*>>()
    private lateinit var checkpointManager: CheckpointManager
    private lateinit var progress: PRProgress
    private lateinit var scoreboard: PRScoreboard
    private var variant = Variant.PIG
    private var speed = 1.0

    override fun prepare() {
        variant = getVariant()

        val properties = map.properties
        val rounds = properties.optInt("rounds", 1)
        speed = properties.optNumber("speed", 0.0).toDouble()

        val team = createTeam()
        val visibilityManager = VisibilityManager(team, Visibility.PARTIALLY_VISIBLE)
        val visibility = VisibilityHandler(visibilityManager, gameHandle.translations, gameHandle.participants)

        visibility.init(gameHandle.hooks)
        initHooks(team, visibilityManager)

        val spawnBounds = mapSchema.spawnBounds!!
        val goal = mapSchema.goal!!

        teleportPlayers(spawnBounds)
        setupCheckpoints(spawnBounds, goal)

        movementObserver.init(gameHandle.scheduler, gameHandle.hooks, gameHandle.server)

        visibility.giveItems(0)

        val progressMarkers = ArrayList(mapSchema.progressMarkers)
        val segmentedPath = createSegmentedPath(augmentPath(mapSchema, rounds), progressMarkers, gameHandle.logger)

        segmentedPath.init(
            gameHandle.participants,
            gameHandle.scheduler,
            gameHandle.hooks,
            gameHandle.server,
            commons().debugController()
        )

        val bossBar = createBossBar(rounds)

        progress = PRProgress(gameHandle, segmentedPath, rounds)
        scoreboard = PRScoreboard(gameHandle, progress, bossBar)

        scoreboard.setup()
    }

    private fun createBossBar(rounds: Int): DynamicTranslatedPlayerBossBar =
        if (rounds > 1) usePlayerDynamicDisplay(
            "game.ap2.pig_race.task_rounds",
            styled(1, YELLOW),
            styled(rounds, YELLOW)
        )
        else usePlayerDynamicTaskDisplay()

    private fun initHooks(team: PlayerTeam, visibilityManager: VisibilityManager) {
        EntityDismountCallback.HOOK.registerWith(hooks) { entity, _ -> entity is ServerPlayer }

        EntityMountCallback.HOOK.registerWith(hooks) { entity, _, _ ->
            val oldVehicle = entity.vehicle
            entity is ServerPlayer && oldVehicle != null && oldVehicle.isAlive
        }

        PlayerTeleportedCallback.HOOK.registerWith(hooks) { player ->
            val pending = pendingEntities.remove(player.uuid) ?: return@registerWith

            val entity = pending.create(player)

            val instance = entity.getAttribute(Attributes.MOVEMENT_SPEED)
            instance?.addPermanentModifier(
                AttributeModifier(
                    gameHandle.gameInfo.identifier("map_boost"),
                    speed,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                )
            )

            gameHandle.scoreboardManager.joinTeam(entity, team)
            visibilityManager.updateVisibilityOf(entity)
        }

        ServerPlayConnectionHooks.DISCONNECT.registerWith(hooks) { handler, _ ->
            handler.player.vehicle?.discard()
        }
    }

    private fun getVariant(): Variant {
        val variantStr = map.properties.optString("variant", Variant.PIG.name.lowercase())
        return try {
            Variant.valueOf(variantStr.uppercase())
        } catch (_: IllegalArgumentException) {
            gameHandle.logger.error("Invalid variant \"{}\"", variantStr)
            Variant.PIG
        }
    }

    private fun augmentPath(schema: PigRaceSchema, rounds: Int): SplinePath {
        val path = schema.path!!

        if (rounds <= 1) return path

        val keypoints = ArrayList(path.keypoints)
        keypoints.addLast(keypoints.first())

        return SplinePath.create(keypoints, gameHandle.logger).orElseThrow()
    }

    override fun go() {
        openGate()

        val hooks = gameHandle.hooks
        val participants = gameHandle.participants

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, participants::isParticipating)
            .then(::resetPlayerToCheckpoint)

        runEveryTick { tick() }

        if (checkpointManager.checkpoints.size > 2) {
            participants.forEach(::giveResetItem)
        }

        PlayerInventoryHooks.SWAP_HANDS.registerWith(hooks) { player, _ ->
            resetPlayerToCheckpoint(player)
            true
        }

        scoreboard.addScoreboardRanking()

        progress.update()
        scoreboard.updateRanking()

        gameHandle.scheduler.interval(Runnable {
            progress.update()
            scoreboard.updateRanking()
        }, 1)

        for (player in gameHandle.participants) {
            PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT)
        }
    }

    private fun tick() {
        for (player in gameHandle.participants) {
            val vehicle = player.vehicle as? LivingEntity ?: continue

            player.remainingFireTicks = 0

            val box = vehicle.type.dimensions.makeBoundingBox(vehicle.position())
            val level = vehicle.level()

            for (pos in BlockPos.betweenClosed(box)) {
                val state = level.getBlockState(pos)

                if (state.isIn(BlockTags.FIRE)
                    || variant != Variant.STRIDER && state.isOf(Blocks.LAVA)
                    || variant == Variant.STRIDER && state.isOf(Blocks.WATER)
                ) {
                    resetPlayerToCheckpoint(player)
                    break
                }
            }

            if (vehicle.isUnderWater) {
                resetPlayerToCheckpoint(player)
            }

            updateCatchupSpeed(player, vehicle)
        }
    }

    private fun updateCatchupSpeed(player: ServerPlayer, vehicle: LivingEntity) {
        val maxDist = progress.getFurthestAbsoluteDistance()
        val playerDist = progress.getAbsoluteDistance(player)

        val dist = max(0.0, maxDist - playerDist)
        val len = CATCHUP_MAX_DISTANCE - CATCHUP_MIN_DISTANCE
        val scale = ((dist - CATCHUP_MIN_DISTANCE) / len).coerceIn(0.0, 1.0)
        val boost = MAX_CATCHUP_BOOST * scale

        val instance = vehicle.getAttribute(Attributes.MOVEMENT_SPEED) ?: return

        val id: Identifier = gameHandle.gameInfo.identifier("catchup")
        val modifier = AttributeModifier(id, boost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        if (instance.hasModifier(id)) {
            instance.addOrUpdateTransientModifier(modifier)
        } else {
            instance.addTransientModifier(modifier)
        }
    }

    private fun createTeam(): PlayerTeam {
        val scoreboardManager: CustomScoreboardManager = gameHandle.scoreboardManager

        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER

        scoreboardManager.joinTeam(gameHandle.participants, team)

        return team
    }

    private fun resetPlayerToCheckpoint(player: ServerPlayer) {
        val checkpoint = checkpointManager.getCheckpoint(player)

        val vehicle = player.vehicle
        if (isVehicle(vehicle)) {
            vehicle!!.discard()
        }

        val pos = checkpoint.pos()
        val x = pos.x() + 0.5
        val y = pos.y()
        val z = pos.z() + 0.5
        val yaw = checkpoint.yaw()

        pendingEntities[player.uuid] = createPending(x, y, z, yaw)
        player.teleportTo(level, x, y, z, emptySet(), yaw, checkpoint.pitch(), true)

        player.remainingFireTicks = 0
    }

    private fun setupCheckpoints(spawnBounds: work.lclpnet.gaco.ds.BlockBox, goal: Checkpoint) {
        val checkpoints = ArrayList(mapSchema.checkpoints)

        val spawn = mapSchema.spawn
        checkpoints.addFirst(Checkpoint(Vec3(spawn.x(), spawn.y(), spawn.z()), spawn.yaw, spawn.pitch, spawnBounds))
        checkpoints.addLast(goal)

        checkpointManager = CheckpointManager(checkpoints, commons().debugController())
        checkpointManager.init(collisionDetector, movementObserver, level)

        CheckpointHelper.notifyWhenReached(checkpointManager, gameHandle.translations)

        movementObserver.whenEntering(goal.bounds(), ::onEnterGoal)
    }

    @Synchronized
    private fun onEnterGoal(player: ServerPlayer) {
        if (winManager.isGameOver || !progress.path.isInLastSegment(player)) return

        val round = progress.getRound(player)

        if (round < progress.rounds) {
            nextRound(player, round)
            return
        }

        Fireworks.spawnGoalFirework(player)
        winnerData.add(player)

        for (other in gameHandle.participants) {
            if (other == player) continue
            distanceData.setScore(other, progress.getAbsoluteRemaining(other))
        }

        winManager.complete()
    }

    private fun nextRound(player: ServerPlayer, round: Int) {
        progress.incrementRound(player)
        checkpointManager.resetCheckpoints(player)
        scoreboard.updateRoundDisplay(player)

        nextRoundSong?.let { song ->
            MusicHelper.playSong(song, 0.5f, player, gameHandle.server, gameHandle.sharedSongCache, gameHandle.logger)
        }

        val text = gameHandle.translations
            .translateText("game.ap2.pig_race.round_title", Component.literal("#${round + 1}").withStyle(YELLOW))
            .formatted(AQUA)
            .translateFor(player)

        Title.get(player).title(Component.empty(), text, 10, 30, 10)

        ParticleHelper.spawnParticleFor(
            ParticleTypes.FIREWORK, player.x, player.y, player.z,
            100, 1.0, 1.0, 1.0, 0.5, listOf(player)
        )
    }

    private fun openGate() {
        val air = Blocks.AIR.defaultBlockState()

        for (bounds in mapSchema.gates) {
            for (pos in bounds) {
                level.setBlockAndUpdate(pos, air)
            }
        }
    }

    private fun teleportPlayers(bounds: work.lclpnet.gaco.ds.BlockBox) {
        val spawn = mapSchema.spawn
        val yaw = spawn.yaw

        for (player in gameHandle.participants) {
            val pos = bounds.randomBlockPos(random)
            val x = pos.x + 0.5
            val y = pos.y.toDouble()
            val z = pos.z + 0.5

            pendingEntities[player.uuid] = createPending(x, y, z, yaw)
            player.teleportTo(level, x, y, z, emptySet(), yaw, 0f, true)

            giveStick(player)
        }
    }

    private fun createPending(x: Double, y: Double, z: Double, yaw: Float): PendingEntity<*> {
        val factory: (ServerLevel) -> LivingEntity = when (variant) {
            Variant.PIG -> { level -> Pig(EntityType.PIG, level) }
            Variant.STRIDER -> { level -> Strider(EntityType.STRIDER, level) }
        }
        return PendingEntity(x, y, z, yaw, factory)
    }

    private fun giveStick(player: ServerPlayer) {
        val translations = gameHandle.translations

        val item = when (variant) {
            Variant.PIG -> Items.CARROT_ON_A_STICK
            Variant.STRIDER -> Items.WARPED_FUNGUS_ON_A_STICK
        }

        val stick = unbreakable(ItemStack(item))
        stick.set(
            DataComponents.CUSTOM_NAME,
            translations.translateText(player, "game.ap2.pig_race.boost")
                .styled { it.withItalic(false).applyFormat(GOLD) }
        )

        player.inventory.setItem(STICK_SLOT, stick)
        PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT)
    }

    private fun giveResetItem(player: ServerPlayer) {
        val translations = gameHandle.translations

        val head: PlayerHead = level.registryAccess()
            .lookupOrThrow(ApRegistries.PLAYER_HEAD)
            .getOptional(PlayerHeads.REDSTONE_BLOCK_REFRESH)
            .orElseThrow()

        val reset = head.createStack()
        reset.set(
            DataComponents.CUSTOM_NAME,
            translations.translateText(player, "ap2.game.reset").formatted(RED)
                .styled { it.withItalic(false) }
        )

        player.inventory.setItem(8, reset)
        PlayerInventoryAccess.setSelectedSlot(player, 4)
    }

    private fun isVehicle(vehicle: Entity?) = vehicle is Pig || vehicle is Strider

    private class PendingEntity<T : LivingEntity>(
        val x: Double, val y: Double, val z: Double, val yaw: Float,
        val factory: (ServerLevel) -> T
    ) {
        fun create(player: ServerPlayer): T {
            val level = player.level()
            val entity = factory(level)

            entity.isInvulnerable = true
            entity.setYBodyRot(yaw)
            entity.setPosRaw(x, y + 0.1, z)
            entity.setItemSlot(EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))

            level.addFreshEntity(entity)
            player.startRiding(entity, true, false)

            return entity
        }
    }
}
