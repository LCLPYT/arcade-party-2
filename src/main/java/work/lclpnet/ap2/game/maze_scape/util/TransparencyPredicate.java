package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;

public class TransparencyPredicate implements Int3Predicate {

    private final FabricStructureWrapper wrapper;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public TransparencyPredicate(FabricStructureWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public boolean test(int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = wrapper.getBlockState(pos);
        return !state.isOpaque();
    }
}
