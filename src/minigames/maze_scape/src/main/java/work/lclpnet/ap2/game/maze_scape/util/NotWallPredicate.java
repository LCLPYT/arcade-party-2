package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.util.math.Matrix3i;

public class NotWallPredicate implements Int3Predicate {

    private final FabricStructureWrapper wrapper;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final Matrix3i transformation;

    public NotWallPredicate(FabricStructureWrapper wrapper, Matrix3i transformation) {
        this.wrapper = wrapper;
        this.transformation = transformation;
    }

    @Override
    public boolean test(int x, int y, int z) {
        transformation.transform(x, y, z, pos);
        BlockState state = wrapper.getBlockState(pos);

        return !state.isCollisionShapeFullBlock(wrapper, pos);
    }
}
