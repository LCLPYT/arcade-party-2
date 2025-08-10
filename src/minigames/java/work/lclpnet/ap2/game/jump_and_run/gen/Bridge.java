package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3f;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

public final class Bridge implements JumpPart {

    private final BlockStructure structure;
    private final BlockBox bounds;
    private final BlockPos spawn;
    private final Direction direction;
    private final BlockPos offset;

    public Bridge(BlockStructure structure, BlockBox bounds, BlockPos spawn, Direction direction, BlockPos offset) {
        this.structure = structure;
        this.bounds = bounds;
        this.spawn = spawn;
        this.direction = direction;
        this.offset = offset;
    }

    @Override
    public BlockStructure structure() {
        return structure;
    }

    @Override
    public Vec3i printOffset() {
        var origin = structure.getOrigin();

        return new Vec3i(
                origin.getX() + offset.getX(),
                origin.getY() + offset.getY(),
                origin.getZ() + offset.getZ()
        );
    }

    @Override
    public Matrix3i printMatrix() {
        return Matrix3i.IDENTITY;
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    public Checkpoint asCheckpoint() {
        Vector3f vec = direction.getUnitVector();
        double yaw = Math.atan2(-vec.x, vec.z);

        return new Checkpoint(spawn, (float) Math.toDegrees(yaw), bounds);
    }

    @Override
    public Bridge transform(BlockPos offset) {
        var matrix = AffineIntMatrix.makeTranslation(offset);

        return new Bridge(structure, bounds.transform(matrix), spawn.add(offset), direction, this.offset.add(offset));
    }
}
