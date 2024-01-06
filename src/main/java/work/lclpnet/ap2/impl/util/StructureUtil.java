package work.lclpnet.ap2.impl.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class StructureUtil {

    public static final EnumSet<StructureWriter.Option> FAST_NO_VIEWERS = EnumSet.of(SKIP_AIR, FORCE_STATE, SKIP_PLAYER_SYNC, SKIP_DROPS);

    public static void placeStructureFast(Printable printable, ServerWorld world) {
        BlockStructure structure = printable.getStructure();
        Vec3i pos = printable.getPrintOffset();

        StructureWriter.placeStructure(structure, world, pos, printable.getPrintMatrix(), FAST_NO_VIEWERS);
    }

    public static void placeStructureFast(BlockStructure structure, ServerWorld world, BlockPos.Mutable pos) {
        StructureWriter.placeStructure(structure, world, pos, Matrix3i.IDENTITY, FAST_NO_VIEWERS);
    }

    public static BlockBox getBounds(BlockStructure structure) {
        return new BlockBox(0, 0, 0,
                structure.getWidth() - 1, structure.getHeight() - 1, structure.getLength() - 1);
    }

    private StructureUtil() {}
}
