package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.math.Matrix3i;
import work.lclpnet.kibu.structure.BlockStructure;

public class StructurePrintable implements Printable {

    private final BlockStructure structure;

    public StructurePrintable(BlockStructure structure) {
        this.structure = structure;
    }

    @Override
    public BlockStructure getStructure() {
        return structure;
    }

    @Override
    public Vec3i getPrintOffset() {
        var origin = structure.getOrigin();
        return new Vec3i(origin.getX(), origin.getY(), origin.getZ());
    }

    @Override
    public Matrix3i getPrintMatrix() {
        return Matrix3i.IDENTITY;
    }
}
