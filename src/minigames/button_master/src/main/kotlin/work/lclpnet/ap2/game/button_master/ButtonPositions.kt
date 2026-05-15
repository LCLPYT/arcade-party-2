package work.lclpnet.ap2.game.button_master

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.VisibilityChecker
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.util.world.BfsContextScanner
import work.lclpnet.ap2.util.world.ScannerNode
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.util.math.Matrix3i
import kotlin.math.max
import kotlin.math.min

class ButtonPositions(
    val world: ServerLevel,
    val map: GameMap,
    val schema: ButtonMasterSchema,
    val commons: GameCommons,
    val gameHandle: MiniGameHandle,
) {
    fun scanWorld(): List<BlockPos> {
        val validNodes = mutableListOf<ScannerNode>()

        findPositionsBfs(validNodes)
        filterDistance(validNodes)
        filterValidButtonPosition(validNodes)
        filterNotVisible(validNodes)

        if (validNodes.isEmpty()) {
            gameHandle.logger.error("Didn't find any valid positions")
            return listOf()
        }

        if (DEBUG_VALID_POSITIONS) {
            debugValidPositions(validNodes)
        }

        return validNodes.map { it.pos }
    }

    private fun debugValidPositions(positions: List<ScannerNode>) {
        val positions = positions.map { it.pos }

        val minPos = positions.first().mutable()
        val maxPos = positions.first().mutable()

        for (pos in positions) {
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

        val mask = StructureMask.createEmpty(BlockBox(minPos, maxPos))

        for (pos in positions) {
            mask.setVoxelAt(pos.x - minPos.x, pos.y - minPos.y, pos.z - minPos.z, true)
        }

        commons.debugController()
            .visualizeStructureMask(mask, minPos, Matrix3i.IDENTITY, Blocks.GREEN_STAINED_GLASS.defaultBlockState())
    }

    fun findPositionsBfs(list: MutableList<ScannerNode>) {
        val box = requireNotNull(schema.scanBox)
        val scanPos = BlockPos.containing(schema.spawn).above()

        val adjacentBlocks = CardinalAdjacentBlocks {
            box.contains(it) && world.getBlockState(it).isAir
        }

        val scanner = BfsContextScanner(adjacentBlocks)
        val it = scanner.scan(scanPos)

        while (it.hasNext()) {
            list.add(it.next())
        }
    }

    fun filterValidButtonPosition(list: MutableList<ScannerNode>) {
        list.retainAll {
            val world = world

            canPlaceButtonAt(world, it.pos)
        }
    }

    fun filterDistance(list: MutableList<ScannerNode>) {
        val minDist = map.properties.getNumber("min-button-distance").toInt()

        list.retainAll {
            it.distance >= minDist
        }
    }

    fun filterNotVisible(list: MutableList<ScannerNode>) {
        val checker = VisibilityChecker(world)

        val spawnEyePos = MapUtils.getSpawnPosition(map).add(0.0, 1.62, 0.0)

        filterNotVisible(spawnEyePos, 0f, 0f, checker, list)
        filterNotVisible(spawnEyePos, 90f, 0f, checker, list)
        filterNotVisible(spawnEyePos, 180f, 0f, checker, list)
        filterNotVisible(spawnEyePos, 270f, 0f, checker, list)
        filterNotVisible(spawnEyePos, 0f, 90f, checker, list)
        filterNotVisible(spawnEyePos, 0f, -90f, checker, list)
    }

    private fun filterNotVisible(
        spawnEyePos: Vec3,
        yaw: Float,
        pitch: Float,
        checker: VisibilityChecker,
        list: MutableList<ScannerNode>
    ) {
        VisibilityChecker.viewProjectionMatrix(
            spawnEyePos.x,
            spawnEyePos.y,
            spawnEyePos.z,
            yaw,
            pitch,
            10,
            Math.PI * 0.5,
            1.0,
            checker.viewProjMat
        )

        list.retainAll {
            notVisibleFromSpawn(checker, it.pos, spawnEyePos)
        }
    }

    private fun notVisibleFromSpawn(
        checker: VisibilityChecker,
        pos: BlockPos,
        cameraPos: Vec3
    ): Boolean {
        return !checker.isBoxVisible(cameraPos, AABB.unitCubeFromLowerCorner(Vec3(pos)), pos.center)
    }

    fun canPlaceButtonAt(world: ServerLevel, pos: BlockPos): Boolean {
        return buttonStates(Blocks.OAK_BUTTON).any {
            it.canSurvive(world, pos)
        }
    }
}

fun buttonStates(block: Block): List<BlockState> {
    return block.stateDefinition.possibleStates.filter {
        it.getValue(ButtonBlock.POWERED) == false && it.getValue(ButtonBlock.FACE) != AttachFace.FLOOR
    }
}
