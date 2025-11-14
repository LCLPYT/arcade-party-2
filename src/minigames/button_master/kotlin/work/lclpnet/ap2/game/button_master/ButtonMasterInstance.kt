package work.lclpnet.ap2.game.button_master

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
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
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.VisibilityChecker
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
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.lobby.game.map.MapUtils
import work.lclpnet.lobby.game.util.BossBarTimer
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

const val DEBUG_VALID_POSITIONS = false
const val DEBUG_BUTTON_POSITION = true
const val DEBUG_CAPSULE_BOUNDS = false
const val EJECT_SECONDS = 15

class ButtonMasterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val schemaHolder: SchemaHolder<ButtonMasterSchema> = useSchema(ButtonMasterSchema::class.java)
    val validPositions = mutableListOf<BlockPos>()
    var currentButtonMarker: Object3d? = null
    var currentButtonPos: BlockPos? = null
    var searchingButton = true
    var capsuleSchematic: BlockStructure? = null
    var ejectTimer: BossBarTimer? = null

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            asset(assetPath("capsule.schem")).use {
                capsuleSchematic = SchematicFormats.SPONGE_V2.reader().read(it, FabricBlockStateAdapter.getInstance())
            }
        }
    }

    override fun prepare() {
        removeExcessCapsules(players().count() - 1)
        scanWorld()
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

    private fun notVisibleFromSpawn(
        checker: VisibilityChecker,
        pos: BlockPos,
        cameraPos: Vec3d
    ): Boolean {
        return !checker.isBoxVisible(cameraPos, Box.from(Vec3d(pos)), pos.toCenterPos())
    }

    fun canPlaceButtonAt(world: ServerWorld, pos: BlockPos): Boolean {
        return Blocks.OAK_BUTTON.stateManager.states.any {
            it.canPlaceAt(world, pos)
        }
    }

    override fun go() {
        nextRound()

        gameHandle.hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, UseBlockCallback { entity, world, hand, result ->
            onUseBlock(entity, world, hand, result)
        })
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

        if (searchingButton) {
            becomeButtonMaster(entity)
            return ActionResult.SUCCESS_SERVER
        }

        // TODO find capsule to open

        return ActionResult.PASS
    }

    fun becomeButtonMaster(player: ServerPlayerEntity) {
        searchingButton = false

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
        ).sendTo(player)

        this.ejectTimer = ejectTimer
    }

    private fun teleportToCapsules(players: List<ServerPlayerEntity>) {
        removeExcessCapsules(players.size)

        // TODO
    }

    private fun removeExcessCapsules(requiredCapsules: Int) {
        val schema = schemaHolder.get()
        val capsules = schema.capsules
        val capsuleSchematic = requireNotNull(capsuleSchematic)

        require(requiredCapsules <= capsules.size) { "Not enough capsules (need $requiredCapsules, got ${capsules.size}" }

        val schematicOffset = capsuleSchematic.origin.toMinecraft()
        val capsuleButton = schema.capsuleButton!!
        val referenceBounds = BlockBox.ofBounds(capsuleSchematic)
        val buttonToOriginOffset = capsuleButton.pos.subtract(schematicOffset)

        for (i in requiredCapsules ..< capsules.size) {
            val capsule = capsules[i]

            val rotation = Matrix3i.makeRotationY(
                    capsule.face.horizontalQuarterTurns - capsuleButton.face.horizontalQuarterTurns
            )

            val localOffset = capsule.pos.subtract(capsuleButton.pos)

            val capsuleBounds = referenceBounds
                .translate(buttonToOriginOffset.multiply(-1))
                .transform(rotation)
                .translate(buttonToOriginOffset)
                .translate(localOffset)
                .translate(schematicOffset)

            if (DEBUG_CAPSULE_BOUNDS) {
                commons().debugController().renderer().ifPresent { it.box(capsuleBounds, Blocks.YELLOW_STAINED_GLASS.defaultState) }
            }

            world.setBlocks(capsuleBounds, Blocks.AIR)
        }
    }

    fun beginNextRound() {
        for (player in players()) {
            gameHandle.worldFacade.teleport(player)

            player.resetAttribute(EntityAttributes.JUMP_STRENGTH)
        }

        nextRound()
    }

    fun nextRound() {
        searchingButton = true

        val lastPos = currentButtonPos

        if (lastPos != null) {
            world.setBlockState(lastPos, Blocks.AIR.defaultState)
        }

        require(validPositions.isNotEmpty()) { "No valid position found" }

        val pos = validPositions.random()

        val buttonBlock = Blocks.BAMBOO_BUTTON

        val states = buttonBlock.stateManager.states.filter { it.canPlaceAt(world, pos) }

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
}
