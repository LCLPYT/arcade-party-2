package work.lclpnet.ap2.game.button_master

import net.minecraft.block.Blocks
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.DyeColor
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4d
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.VisibilityChecker
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.players
import work.lclpnet.ap2.teleport
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.lobby.game.map.MapUtils
import java.lang.Math.toRadians
import kotlin.math.max
import kotlin.math.min

const val DEBUG_VALID_POSITIONS = false
const val DEBUG_BUTTON_POSITION = true

class ButtonMasterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<ButtonMasterSchema> = useSchema(ButtonMasterSchema::class.java)
    val validPositions = mutableListOf<BlockPos>()
    var currentButtonMarker: Object3d? = null
    var currentButtonPos: BlockPos? = null

    override fun prepare() {
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
    }

    fun nextRound() {
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

    private fun teleportToSpawn() {
        val spawnPos = MapUtils.getSpawnPosition(map)
        val spawnYaw = MapUtils.getSpawnYaw(map)

        players().forEach {
            it.teleport(spawnPos, spawnYaw)
        }
    }
}
