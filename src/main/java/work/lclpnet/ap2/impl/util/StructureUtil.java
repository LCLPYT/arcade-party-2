package work.lclpnet.ap2.impl.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.math.Matrix3i;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;

public class StructureUtil {

    public static void placeStructure(BlockStructure structure, ServerWorld world, Vec3i pos) {
        placeStructure(structure, world, pos, null);
    }

    public static void placeStructure(BlockStructure structure, ServerWorld world, Vec3i pos, @Nullable Matrix3i transformation) {
        var origin = structure.getOrigin();

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();

        var adapter = FabricBlockStateAdapter.getInstance();
        BlockState air = Blocks.AIR.getDefaultState();

        BlockPos.Mutable printPos = new BlockPos.Mutable();

        for (var kibuPos : structure.getBlockPositions()) {
            int sx = kibuPos.getX() - ox, sy = kibuPos.getY() - oy, sz = kibuPos.getZ() - oz;

            if (transformation != null) {
                transformation.transform(sx, sy, sz, printPos);
                printPos.set(px + printPos.getX(), py + printPos.getY(), pz + printPos.getZ());
            } else {
                printPos.set(px + sx, py + sy, pz + sz);
            }

            var kibuState = structure.getBlockState(kibuPos);

            BlockState state = adapter.revert(kibuState);
            if (state == null) state = air;

            world.setBlockState(printPos, state, 0);
        }
    }

    public static void placeStructure(Printable printable, ServerWorld world) {
        placeStructure(printable.getStructure(), world, printable.getPrintOffset(), printable.getPrintMatrix());
    }

    public static BlockBox getBounds(BlockStructure structure) {
        return new BlockBox(0, 0, 0,
                structure.getWidth() - 1, structure.getHeight() - 1, structure.getLength() - 1);
    }

    private StructureUtil() {}
}
