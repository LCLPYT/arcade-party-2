package work.lclpnet.ap2.game.button_master

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ButtonBlock
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.scoreboard.AbstractTeam
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.DyeColor
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.joml.Vector3d
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.asVec3d
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.VisibilityChecker
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.players
import work.lclpnet.ap2.resetAttribute
import work.lclpnet.ap2.setAttribute
import work.lclpnet.ap2.setBlocks
import work.lclpnet.ap2.teleport
import work.lclpnet.ap2.toMinecraft
import work.lclpnet.ap2.translate
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.gaco.math.BlockFace
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.lobby.game.map.MapUtils
import work.lclpnet.lobby.game.util.BossBarTimer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

const val DEBUG_VALID_POSITIONS = false
const val DEBUG_BUTTON_POSITION = false
const val DEBUG_CAPSULE_BOUNDS = false
const val DEBUG_CAPSULE_SPAWNS = false
const val EJECT_SECONDS = 15

enum class GameState {
    SEARCHING_BUTTON,
    CHOOSE_EJECT,
    EJECTING,
    IDLE
}

class ButtonMasterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val schemaHolder: SchemaHolder<ButtonMasterSchema> = useSchema(ButtonMasterSchema::class.java)
    val validPositions = mutableListOf<BlockPos>()
    var currentButtonMarker: Object3d? = null
    var currentButtonPos: BlockPos? = null
    var gameState = GameState.IDLE
    var capsuleSchematic: BlockStructure? = null
    var ejectTimer: BossBarTimer? = null
    val capsuleButtons = mutableMapOf<BlockPos, BlockFace>()
    val capsulePlayers = mutableMapOf<BlockFace, UUID>()
    var buttonMasterUuid: UUID? = null
    var ejectedPlayer: UUID? = null
    var task: TaskHandle? = null
    var taskBar: TranslatedBossBar? = null

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            asset(assetPath("capsule.schem")).use {
                capsuleSchematic = SchematicFormats.SPONGE_V2.reader().read(it, FabricBlockStateAdapter.getInstance())
            }
        }
    }

    override fun prepare() {
        scanWorld()
        setupCapsules()
        setupTeam()
    }

    private fun setupTeam() {
        val scoreboardManager = gameHandle.getScoreboardManager()
        val team = scoreboardManager.createTeam("team")
        team.nameTagVisibilityRule = AbstractTeam.VisibilityRule.NEVER
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team)
    }

    fun scanWorld() {
        val world = world
        val schema = schemaHolder.get()
        val box = requireNotNull(schema.scanBox)
        val scanPos = requireNotNull(schema.scanPos)

        val adjacentBlocks = CardinalAdjacentBlocks {
            box.contains(it) && world.getBlockState(it).isAir
        }

        val scanner = BfsWorldScanner(adjacentBlocks)
        val it = scanner.scan(scanPos)
        val checker = VisibilityChecker(world)

        val spawnPos = MapUtils.getSpawnPosition(map).add(0.0, 1.62, 0.0)
        val spawnYaw = MapUtils.getSpawnYaw(map)

        VisibilityChecker.viewProjectionMatrix(
            spawnPos.x,
            spawnPos.y,
            spawnPos.z,
            spawnYaw,
            0f,
            10,
            VisibilityChecker.PLAYER_FOV,
            VisibilityChecker.PLAYER_ASPECT_RATIO,
            checker.viewProjMat
        )

        while (it.hasNext()) {
            val pos = it.next()

            if (canPlaceButtonAt(world, pos) && notVisibleFromSpawn(checker, pos, spawnPos)) {
                validPositions.add(pos)
            }
        }

        if (validPositions.isEmpty()) {
            gameHandle.logger.error("Didn't find any valid positions")
            return
        }

        val minPos = validPositions.first().mutableCopy()
        val maxPos = validPositions.first().mutableCopy()

        for (pos in validPositions) {
            minPos.set(
                min(minPos.x, pos.x),
                min(minPos.y, pos.y),
                min(minPos.z, pos.z),
            )
            maxPos.set(
                max(maxPos.x, pos.x),
                max(maxPos.y, pos.y),
                max(maxPos.z, pos.z),
            )
        }

        if (DEBUG_VALID_POSITIONS) {
            val mask = StructureMask.createEmpty(BlockBox(minPos, maxPos))

            for (pos in validPositions) {
                mask.setVoxelAt(pos.x - minPos.x, pos.y - minPos.y, pos.z - minPos.z, true)
            }

            commons().debugController().visualizeStructureMask(mask, minPos, Matrix3i.IDENTITY, Blocks.GREEN_STAINED_GLASS.defaultState)
        }
    }

    private fun setupCapsules() {
        val schema = schemaHolder.get()

        for (capsule in schema.capsules) {
            capsuleButtons[capsule.pos] = capsule

            if (DEBUG_CAPSULE_BOUNDS) {
                val capsuleBounds = getCapsuleBounds(capsule)

                commons().debugController().renderer().ifPresent {
                    it.box(capsuleBounds, Blocks.YELLOW_STAINED_GLASS.defaultState)
                }
            }

            if (DEBUG_CAPSULE_SPAWNS) {
                val capsuleSpawn = getCapsuleSpawn(capsule)

                commons().debugController().renderer().ifPresent {
                    it.arrow(capsuleSpawn.asVec3d(), MathUtil.yaw2vec(capsuleSpawn.yaw), Blocks.LIME_TERRACOTTA.defaultState)
                }
            }
        }
    }

    private fun notVisibleFromSpawn(
        checker: VisibilityChecker,
        pos: BlockPos,
        cameraPos: Vec3d
    ): Boolean {
        return !checker.isBoxVisible(cameraPos, Box.from(Vec3d(pos)), pos.toCenterPos())
    }

    fun canPlaceButtonAt(world: ServerWorld, pos: BlockPos): Boolean {
        return buttonStates(Blocks.OAK_BUTTON).any {
            it.canPlaceAt(world, pos)
        }
    }

    override fun go() {
        taskBar = useTaskDisplay()

        nextRound()

        gameHandle.hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, UseBlockCallback { entity, world, hand, result ->
            onUseBlock(entity, world, hand, result)
        })

        eliminateBelowCriticalHeight()
    }

    fun onUseBlock(
        entity: PlayerEntity,
        _world: World,
        hand: Hand,
        result: BlockHitResult
    ): ActionResult {
        if (entity !is ServerPlayerEntity) {
            return ActionResult.PASS
        }

        val state = world.getBlockState(result.blockPos)

        if (!state.isIn(BlockTags.BUTTONS))
            return ActionResult.PASS

        if (gameState == GameState.SEARCHING_BUTTON) {
            becomeButtonMaster(entity)
            return ActionResult.SUCCESS_SERVER
        }

        if (gameState != GameState.CHOOSE_EJECT || buttonMasterUuid != entity.uuid)
            return ActionResult.PASS

        val capsule = capsuleButtons[result.blockPos] ?: return ActionResult.PASS

        eject(capsule)

        return ActionResult.PASS
    }

    private fun eject(capsule: BlockFace) {
        gameState = GameState.EJECTING

        val spawn = getCapsuleSpawn(capsule)

        world.setBlockState(BlockPos.ofFloored(spawn).down(), Blocks.AIR.defaultState)

        val uuid = capsulePlayers[capsule] ?: return
        val player = players().getParticipant(uuid).orElse(null) ?: return

        task = gameHandle.scheduler.timeout(Ticks.seconds(5), Runnable {
            eliminate(player)
        })
    }

    fun becomeButtonMaster(player: ServerPlayerEntity) {
        buttonMasterUuid = player.uuid
        gameState = GameState.CHOOSE_EJECT
        taskBar?.isVisible = false

        player.teleport(schemaHolder.get().buttonMasterSpawn!!)
        player.setAttribute(EntityAttributes.JUMP_STRENGTH, 0.0)

        teleportToCapsules(players().filter { it != player })

        val ejectTimer = commons().createTimer(
            translate("game.ap2.button_master.eject"),
            EJECT_SECONDS,
        )

        ejectTimer.whenDone {
            eliminate(player)

            if (!winManager.isGameOver) {
                beginNextRound()
            }
        }

        translate(
            "game.ap2.button_master.choose_capsule",
            styled(EJECT_SECONDS, Formatting.YELLOW)
        ).formatted(Formatting.AQUA).sendTo(player)

        this.ejectTimer = ejectTimer
    }

    private fun teleportToCapsules(players: List<ServerPlayerEntity>) {
        removeExcessCapsules(players.size)

        val schema = schemaHolder.get()
        val capsules = schema.capsules

        capsulePlayers.clear()

        for ((i, player) in players.shuffled().withIndex()) {
            val spawn = getCapsuleSpawn(capsules[i])
            player.teleport(spawn)

            capsulePlayers[capsules[i]] = player.uuid
        }
    }

    fun getCapsuleSpawn(capsule: BlockFace): PositionRotation {
        val schema = schemaHolder.get()
        val referenceSpawn = schema.capsuleSpawn!!.asVec3d()
        val referenceButton = schema.capsuleButton!!
        val schematicOffset = requireNotNull(capsuleSchematic).origin.toMinecraft()
        val buttonToOriginOffset = referenceButton.pos.subtract(schematicOffset)
        val localSpawn = referenceSpawn.subtract(referenceButton.pos.toCenterPos())

        val rotation = Matrix3i.makeRotationY(
            capsule.face.horizontalQuarterTurns - referenceButton.face.horizontalQuarterTurns
        )

        val localOffset = capsule.pos.subtract(referenceButton.pos)

        val capsuleSpawn = rotation.transform(localSpawn)
            .add(localOffset.toCenterPos())
            .add(Vec3d.of(buttonToOriginOffset))
            .add(Vec3d.of(schematicOffset))

        val yaw = MathUtil.rotateYaw(schema.capsuleSpawn.yaw, rotation, Vector3d())

        return PositionRotation(capsuleSpawn.x, capsuleSpawn.y, capsuleSpawn.z, yaw, 0f)
    }

    fun getCapsuleBounds(capsule: BlockFace): BlockBox {
        val schema = schemaHolder.get()
        val capsuleSchematic = requireNotNull(capsuleSchematic)
        val schematicOffset = capsuleSchematic.origin.toMinecraft()
        val capsuleButton = schema.capsuleButton!!
        val referenceBounds = BlockBox.ofBounds(capsuleSchematic)
        val buttonToOriginOffset = capsuleButton.pos.subtract(schematicOffset)

        val rotation = Matrix3i.makeRotationY(
            capsule.face.horizontalQuarterTurns - capsuleButton.face.horizontalQuarterTurns
        )

        val localOffset = capsule.pos.subtract(capsuleButton.pos)

        return referenceBounds
            .translate(buttonToOriginOffset.multiply(-1))
            .transform(rotation)
            .translate(buttonToOriginOffset)
            .translate(localOffset)
            .translate(schematicOffset)
    }

    private fun removeExcessCapsules(requiredCapsules: Int) {
        val schema = schemaHolder.get()
        val capsules = schema.capsules

        require(requiredCapsules <= capsules.size) { "Not enough capsules (need $requiredCapsules, got ${capsules.size}" }

        for (i in requiredCapsules ..< capsules.size) {
            val capsule = capsules[i]
            val capsuleBounds = getCapsuleBounds(capsule)

            world.setBlocks(capsuleBounds, Blocks.AIR)
        }
    }

    fun beginNextRound() {
        buttonMasterUuid = null
        ejectedPlayer = null

        task?.cancel()
        task = null

        ejectTimer?.stop()
        ejectTimer = null

        for (player in players()) {
            gameHandle.worldFacade.teleport(player)

            player.resetAttribute(EntityAttributes.JUMP_STRENGTH)
        }

        nextRound()
    }

    fun nextRound() {
        gameState = GameState.SEARCHING_BUTTON
        taskBar?.isVisible = true

        removeExcessCapsules(players().count() - 1)

        val lastPos = currentButtonPos

        if (lastPos != null) {
            world.setBlockState(lastPos, Blocks.AIR.defaultState)
        }

        require(validPositions.isNotEmpty()) { "No valid position found" }

        val pos = validPositions.random()

        val buttonBlock = Blocks.BAMBOO_BUTTON

        val states = buttonStates(buttonBlock).filter { it.canPlaceAt(world, pos) }

        require(states.isNotEmpty()) { "No valid button state found" }

        val state = states.random()

        world.setBlockState(pos, state)

        if (DEBUG_BUTTON_POSITION) {
            currentButtonMarker?.detach()

            commons().debugController().renderer().ifPresent {
                currentButtonMarker = it.marker(pos.toCenterPos(), Blocks.BLUE_STAINED_GLASS.defaultState, DyeColor.BLUE.entityColor)
            }
        }
    }

    fun buttonStates(block: Block): List<BlockState> {
        return block.stateManager.states.filter { it.get(ButtonBlock.POWERED) == false }
    }

    override fun onEliminated(player: ServerPlayerEntity?) {
        super.onEliminated(player)

        if (winManager.isGameOver || gameState == GameState.SEARCHING_BUTTON) return

        beginNextRound()
    }
}
