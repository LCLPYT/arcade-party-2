package work.lclpnet.ap2.game.button_master

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ButtonBlock
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.VisibilityChecker
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
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

        val validPositions = mutableListOf<BlockPos>()

        while (it.hasNext()) {
            val pos = it.next()

            if (canPlaceButtonAt(world, pos) && notVisibleFromSpawn(checker, pos, spawnPos)) {
                validPositions.add(pos)
            }
        }

        if (validPositions.isEmpty()) {
            gameHandle.logger.error("Didn't find any valid positions")
            return listOf()
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

            commons.debugController().visualizeStructureMask(mask, minPos, Matrix3i.IDENTITY, Blocks.GREEN_STAINED_GLASS.defaultState)
        }

        return validPositions
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
    return block.stateManager.states.filter { it.get(ButtonBlock.POWERED) == false }
}
