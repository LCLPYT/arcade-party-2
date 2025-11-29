package work.lclpnet.ap2.game.button_master

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ButtonBlock
import net.minecraft.block.enums.BlockFace
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.VisibilityChecker
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.util.world.BfsContextScanner
import work.lclpnet.ap2.util.world.ScannerNode
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.lobby.game.map.MapUtils
import kotlin.math.max
import kotlin.math.min

class ButtonPositions(
    val world: ServerWorld,
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

        val minPos = positions.first().mutableCopy()
        val maxPos = positions.first().mutableCopy()

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
            .visualizeStructureMask(mask, minPos, Matrix3i.IDENTITY, Blocks.GREEN_STAINED_GLASS.defaultState)
    }

    fun findPositionsBfs(list: MutableList<ScannerNode>) {
        val box = requireNotNull(schema.scanBox)
        val scanPos = BlockPos.ofFloored(schema.spawn).up()

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
        spawnEyePos: Vec3d,
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
        cameraPos: Vec3d
    ): Boolean {
        return !checker.isBoxVisible(cameraPos, Box.from(Vec3d(pos)), pos.toCenterPos())
    }

    fun canPlaceButtonAt(world: ServerWorld, pos: BlockPos): Boolean {
        return buttonStates(Blocks.OAK_BUTTON).any {
            it.canPlaceAt(world, pos)
        }
    }
}

fun buttonStates(block: Block): List<BlockState> {
    return block.stateManager.states.filter {
        it.get(ButtonBlock.POWERED) == false && it.get(ButtonBlock.FACE) != BlockFace.FLOOR
    }
}
