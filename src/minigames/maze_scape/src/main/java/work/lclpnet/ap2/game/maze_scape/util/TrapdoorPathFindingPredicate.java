package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.ap2.impl.ai.PathFindingPredicate;

import static net.minecraft.world.level.block.TrapDoorBlock.OPEN;

public class TrapdoorPathFindingPredicate implements PathFindingPredicate {

    private TrapdoorPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, Mob entity, BlockPos from) {
        Level world = entity.level();
        BlockState fromState = world.getBlockState(from.below());
        BlockState toState = entity.level().getBlockState(new BlockPos(x, y - 1, z));

        return !isOpenTrapdoor(fromState) || !isOpenTrapdoor(toState) || y != from.getY();
    }

    private boolean isOpenTrapdoor(BlockState state) {
        return state.is(BlockTags.TRAPDOORS) && state.hasProperty(OPEN) && state.getValue(TrapDoorBlock.OPEN);
    }

    public static TrapdoorPathFindingPredicate getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final TrapdoorPathFindingPredicate instance = new TrapdoorPathFindingPredicate();
    }
}
