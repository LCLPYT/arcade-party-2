package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Objects;

public final class OrientedPart implements JumpPart {

    private final BlockStructure structure;
    private final Vec3i offset;
    private final int rotation;
    private final BlockBox bounds;
    private final @Nullable Connector in, out;
    private final RoomInfo info;
    private final Matrix3i matrix;

    public OrientedPart(BlockStructure structure, Vec3i offset, int rotation, @Nullable Connector in, @Nullable Connector out, @Nullable RoomData data) {
        this.structure = structure;
        this.offset = offset;
        this.rotation = rotation;
        this.matrix = Matrix3i.makeRotationY(rotation);

        AffineIntMatrix mat4 = new AffineIntMatrix(matrix).translate(offset);

        this.bounds = StructureUtil.getBounds(structure).transform(mat4);
        this.in = in != null ? in.transform(mat4) : null;
        this.out = out != null ? out.transform(mat4) : null;

        RoomData transformedData = data != null ? data.transform(mat4) : null;
        this.info = new RoomInfo(bounds, transformedData);
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public BlockStructure structure() {
        return structure;
    }

    public Vec3i offset() {
        return offset;
    }

    public int rotation() {
        return rotation;
    }

    @Nullable
    public Connector in() {
        return in;
    }

    @Nullable
    public Connector out() {
        return out;
    }

    public RoomInfo info() {
        return info;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (OrientedPart) obj;
        return Objects.equals(this.structure, that.structure) &&
                Objects.equals(this.offset, that.offset) &&
                this.rotation == that.rotation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(structure, offset, rotation);
    }

    @Override
    public String toString() {
        return "OrientedPart[" +
                "structure=" + structure + ", " +
                "offset=" + offset + ", " +
                "rotation=" + rotation + ']';
    }

    @Override
    public Vec3i printOffset() {
        return offset;
    }

    @Override
    public Matrix3i printMatrix() {
        return matrix;
    }
}
