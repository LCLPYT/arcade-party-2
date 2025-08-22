package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
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

    public OrientedPart(BlockStructure structure, Vec3i offset, int rotation, BlockBox bounds,
                        @Nullable Connector in, @Nullable Connector out, RoomInfo info) {
        this.structure = structure;
        this.offset = offset;
        this.rotation = rotation;
        this.matrix = Matrix3i.makeRotationY(rotation);
        this.bounds = bounds;
        this.in = in;
        this.out = out;
        this.info = info;
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
    public OrientedPart transform(BlockPos offset) {
        var mat4 = AffineIntMatrix.makeTranslation(offset);

        Vec3i translatedOffset = this.offset.add(offset);
        BlockBox transformedBounds = this.bounds.transform(mat4);
        var translatedIn = in != null ? in.transform(mat4) : null;
        var translatedOut = out != null ? out.transform(mat4) : null;
        var translatedData = info.transform(mat4);

        return new OrientedPart(structure, translatedOffset, rotation, transformedBounds, translatedIn, translatedOut, translatedData);
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

    public static OrientedPart createTransformed(BlockStructure structure, Vec3i offset, int rotation,
                                                 @Nullable Connector in, @Nullable Connector out,
                                                 @Nullable RoomData data) {

        var mat4 = new AffineIntMatrix(Matrix3i.makeRotationY(rotation)).translate(offset);

        BlockBox bounds = StructureUtil.getBounds(structure).transform(mat4);
        Connector transformedIn = in != null ? in.transform(mat4) : null;
        Connector transformedOut = out != null ? out.transform(mat4) : null;
        RoomData transformedData = data != null ? data.transform(mat4) : null;

        var info = new RoomInfo(bounds, transformedData);

        return new OrientedPart(structure, offset, rotation, bounds, transformedIn, transformedOut, info);
    }
}
