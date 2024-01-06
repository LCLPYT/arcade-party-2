package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3f;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

public class Bridge implements Printable {

    private final BlockStructure structure;
    private final BlockBox bounds;
    private final BlockPos spawn;
    private final Direction direction;

    public Bridge(BlockStructure structure, BlockBox bounds, BlockPos spawn, Direction direction) {
        this.structure = structure;
        this.bounds = bounds;
        this.spawn = spawn;
        this.direction = direction;
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

    public BlockBox getBounds() {
        return bounds;
    }

    public JumpPart asJumpPart() {
        return new JumpPart(this, bounds);
    }

    public Checkpoint asCheckpoint() {
        Vector3f vec = direction.getUnitVector();
        double yaw = Math.atan2(-vec.x, vec.z);

        return new Checkpoint(spawn, (float) Math.toDegrees(yaw), bounds);
    }
}
