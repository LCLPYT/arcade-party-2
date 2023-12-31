package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.Matrix4i;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Objects;

public final class OrientedPart implements Printable {

    private final BlockStructure structure;
    private final Vec3i offset;
    private final int rotation;
    private final BlockBox bounds;
    private final Connector in, out;
    private final Matrix3i matrix;

    public OrientedPart(BlockStructure structure, Vec3i offset, int rotation, Connector in, Connector out) {
        this.structure = structure;
        this.offset = offset;
        this.rotation = rotation;

        this.matrix = Matrix3i.makeRotationY(rotation);

        Matrix4i mat4 = new Matrix4i(matrix).translate(offset);

        this.bounds = StructureUtil.getBounds(structure).transform(mat4);
        this.in = in != null ? in.transform(mat4) : null;
        this.out = out != null ? out.transform(mat4) : null;
    }

    public BlockBox getBounds() {
        return bounds;
    }

    public BlockStructure structure() {
        return structure;
    }

    public Vec3i offset() {
        return offset;
    }

    public int rotation() {
        return rotation;
    }

    public Connector getIn() {
        return in;
    }

    public Connector getOut() {
        return out;
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
    public BlockStructure getStructure() {
        return structure;
    }

    @Override
    public Vec3i getPrintOffset() {
        return offset;
    }

    @Override
    public Matrix3i getPrintMatrix() {
        return matrix;
    }

    public JumpPart asJumpPart() {
        return new JumpPart(this, bounds);
    }
}
