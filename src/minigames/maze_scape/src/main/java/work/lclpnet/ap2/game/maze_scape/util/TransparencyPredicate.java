package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;

public class TransparencyPredicate implements Int3Predicate {

    private final FabricStructureWrapper wrapper;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public TransparencyPredicate(FabricStructureWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public boolean test(int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = wrapper.getBlockState(pos);
        return !state.canOcclude();
    }
}
