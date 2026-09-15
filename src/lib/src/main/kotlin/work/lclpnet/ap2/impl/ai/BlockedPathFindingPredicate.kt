package work.lclpnet.ap2.impl.ai

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.TrapDoorBlock
import work.lclpnet.ap2.ext.mc.isIn
import kotlin.math.abs

object BlockedPathFindingPredicate : PathFindingPredicate {

    override fun canReach(x: Int, y: Int, z: Int, entity: Mob, from: BlockPos): Boolean {
        val world = entity.level()
        val to = BlockPos(x, y, z)
        val prev = BlockPos.MutableBlockPos()

        val dx = x - from.x
        val dz = z - from.z

        var dir = Direction.getNearest(dx, 0, dz, null)

        if (dir != null) {
            return !isBidiBlocked(world, to, dir, prev, entity)
        }

        // check horizontal diagonal
        if (abs(dx) != 1 || abs(dz) != 1) {
            return true
        }

        // check x direction first
        dir = Direction.getNearest(dx, 0, 0, null)

        if (dir != null && isBidiBlocked(world, to, dir, prev, entity)) {
            return false
        }

        // then check z direction
        dir = Direction.getNearest(0, 0, dz, null)

        return dir == null || !isBidiBlocked(world, to, dir, prev, entity)
    }


    private fun isBidiBlocked(
        world: Level,
        to: BlockPos,
        dir: Direction,
        from: BlockPos.MutableBlockPos,
        entity: Mob
    ): Boolean {
        from.set(
            to.x - dir.stepX,
            to.y - dir.stepY,
            to.z - dir.stepZ
        )

        // check if current position is blocked
        if (isBlockedByTrapdoor(world, to, dir, from, entity)) {
            return true
        }

        // check if the previous position is blocked
        return isBlockedByTrapdoor(world, from, dir.opposite, null, entity)
    }

    private fun isBlockedByTrapdoor(
        world: Level,
        pos: BlockPos,
        dir: Direction,
        from: BlockPos?,
        entity: Mob
    ): Boolean {
        var state = world.getBlockState(pos)

        if (!state.isIn(BlockTags.TRAPDOORS)
            || !state.hasProperty(TrapDoorBlock.FACING)
            || !state.hasProperty(TrapDoorBlock.OPEN)
            || !state.getValue(TrapDoorBlock.OPEN)
            || state.getValue(TrapDoorBlock.FACING) != dir
        ) {
            return false
        }

        // direct way is blocked by trapdoor, check if there is space to jump over the trapdoor
        val box = entity.getDimensions(entity.pose).makeBoundingBox(
            pos.x + 0.5,
            (pos.y + 1).toDouble(),
            pos.z + 0.5
        )

        val blockCollisions = world.getBlockCollisions(entity, box)

        if (blockCollisions.iterator().hasNext()) {
            return true
        }

        if (from == null) {
            return false
        }

        // make sure the position from where to jump is safe
        val jumpSurface = from.below()
        state = world.getBlockState(jumpSurface)

        return !state.isFaceSturdy(world, jumpSurface, Direction.UP)
    }
}
