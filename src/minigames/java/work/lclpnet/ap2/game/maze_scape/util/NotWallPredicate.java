package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.util.math.Matrix3i;

public class NotWallPredicate implements Int3Predicate {

    private final FabricStructureWrapper wrapper;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final Matrix3i transformation;

    public NotWallPredicate(FabricStructureWrapper wrapper, Matrix3i transformation) {
        this.wrapper = wrapper;
        this.transformation = transformation;
    }

    @Override
    public boolean test(int x, int y, int z) {
        transformation.transform(x, y, z, pos);
        BlockState state = wrapper.getBlockState(pos);

        return !state.isFullCube(wrapper, pos);
    }
}
