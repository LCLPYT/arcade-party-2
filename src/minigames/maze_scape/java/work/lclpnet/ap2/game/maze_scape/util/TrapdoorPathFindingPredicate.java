package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;

import static net.minecraft.block.TrapdoorBlock.OPEN;

public class TrapdoorPathFindingPredicate implements PathFindingPredicate {

    private TrapdoorPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from) {
        World world = entity.getEntityWorld();
        BlockState fromState = world.getBlockState(from.down());
        BlockState toState = entity.getEntityWorld().getBlockState(new BlockPos(x, y - 1, z));

        return !isOpenTrapdoor(fromState) || !isOpenTrapdoor(toState) || y != from.getY();
    }

    private boolean isOpenTrapdoor(BlockState state) {
        return state.isIn(BlockTags.TRAPDOORS) && state.contains(OPEN) && state.get(TrapdoorBlock.OPEN);
    }

    public static TrapdoorPathFindingPredicate getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final TrapdoorPathFindingPredicate instance = new TrapdoorPathFindingPredicate();
    }
}
