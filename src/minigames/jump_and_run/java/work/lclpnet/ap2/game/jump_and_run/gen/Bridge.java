package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.AffineIntMatrix;
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

    public BlockPos spawn() {
        return spawn;
    }

    @Override
    public Bridge transform(BlockPos offset) {
        var matrix = AffineIntMatrix.makeTranslation(offset);

        return new Bridge(structure, bounds.transform(matrix), spawn.offset(offset), direction, this.offset.offset(offset));
    }
}
