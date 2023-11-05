package work.lclpnet.ap2.impl.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;

public class StructureUtil {

    public static void placeStructure(BlockStructure structure, ServerWorld world, BlockPos pos) {
        var origin = structure.getOrigin();

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();

        var adapter = FabricBlockStateAdapter.getInstance();
        BlockState air = Blocks.AIR.getDefaultState();

        BlockPos.Mutable printPos = new BlockPos.Mutable();

        for (var kibuPos : structure.getBlockPositions()) {
            printPos.set(
                    kibuPos.getX() - ox + px,
                    kibuPos.getY() - oy + py,
                    kibuPos.getZ() - oz + pz
            );

            var kibuState = structure.getBlockState(kibuPos);

            BlockState state = adapter.revert(kibuState);
            if (state == null) state = air;

            world.setBlockState(printPos, state, 0);
        }
    }

    private StructureUtil() {}
}
