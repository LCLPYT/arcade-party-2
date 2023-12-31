package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

public record Connector(BlockPos pos, Direction direction) {

    public Connector transform(AffineIntMatrix mat4) {
        Vec3i vec = mat4.transformVector(this.direction.getVector());
        Direction dir = Direction.fromVector(vec.getX(), vec.getY(), vec.getZ());

        if (dir == null) throw new IllegalArgumentException("Invalid transformation: Direction is not canonical");

        return new Connector(mat4.transform(pos), dir);
    }
}
