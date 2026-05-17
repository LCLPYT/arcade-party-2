package work.lclpnet.ap2.game.mining_battle

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.gaco.ds.BlockBox

class MiningBattleGenerator(
    private val ore: MiningBattleOre,
    private val box: BlockBox,
    private val material: Set<BlockState>
) {

    fun generateOre(world: ServerLevel) {
        val rel = BlockPos.MutableBlockPos()
        val positions = scanWorld(world, rel)
        placeOres(world, positions, rel)
    }

    private fun placeOres(world: ServerLevel, positions: LongList, rel: BlockPos.MutableBlockPos) {
        val it = positions.longIterator()
        while (it.hasNext()) {
            val packed = it.nextLong()
            val newState = ore.getRandomState() ?: continue
            rel.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed))
            world.setBlock(rel, newState, Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS)
        }
    }

    private fun scanWorld(world: ServerLevel, rel: BlockPos.MutableBlockPos): LongList {
        val positions: LongList = LongArrayList()

        for (pos in box) {
            val state = world.getBlockState(pos)
            if (!material.contains(state) || isExposed(world, pos, rel)) continue
            positions.add(pos.asLong())
        }

        return positions
    }

    private fun isExposed(world: ServerLevel, pos: BlockPos, rel: BlockPos.MutableBlockPos): Boolean {
        val x = pos.x
        val y = pos.y
        val z = pos.z

        for (dir in Direction.entries) {
            rel.set(x + dir.stepX, y + dir.stepY, z + dir.stepZ)
            if (!world.getBlockState(rel).isSolidRender) return true
        }

        return false
    }
}
