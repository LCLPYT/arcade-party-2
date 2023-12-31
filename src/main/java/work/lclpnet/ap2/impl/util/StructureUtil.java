package work.lclpnet.ap2.impl.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;

public class StructureUtil {

    public static void placeStructure(Printable printable, ServerWorld world) {
        BlockStructure structure = printable.getStructure();
        Vec3i pos = printable.getPrintOffset();

        StructureWriter.placeStructure(structure, world, pos, printable.getPrintMatrix());
    }

    public static BlockBox getBounds(BlockStructure structure) {
        return new BlockBox(0, 0, 0,
                structure.getWidth() - 1, structure.getHeight() - 1, structure.getLength() - 1);
    }

    private StructureUtil() {}
}
