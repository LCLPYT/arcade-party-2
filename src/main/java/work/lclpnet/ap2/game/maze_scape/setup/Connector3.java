package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.enums.Orientation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

public record Connector3(BlockPos pos, Orientation orientation, String name, String target) {

    public Vec3i direction() {
        return orientation.getFacing().getVector();
    }

    public int rotateToFace(Connector3 other) {
        return rotateToFace(this.orientation.getFacing(), other.orientation.getFacing());
    }

    @Nullable
    public Connector3 createOpposing() {
        Direction facing = orientation.getFacing();

        Orientation opposingOrientation = Orientation.byDirections(facing.getOpposite(), orientation.getRotation());

        if (opposingOrientation == null) return null;

        BlockPos opposingPos = pos.add(facing.getVector());

        // target and name must be inverted for the opposing connector
        return new Connector3(opposingPos, opposingOrientation, target, name);
    }

    /**
     * Calculates the amount that {@code other} has to be rotated in order to face {@code face}.
     * In other words, when rotating {@code other} by the amount returned by this method, it will be opposite to {@code face}.
     * @param face The direction that the other vector should face.
     * @param other The direction to rotate.
     * @return The amount of counter-clockwise rotation as a multiple of 90 degrees. (1=90, 2=180 ...)
     */
    public static int rotateToFace(Direction face, Direction other) {
        int rotation = face.getHorizontal();
        int otherRotation = other.getHorizontal();

        return Math.floorMod(-1 * (rotation - otherRotation + 2), 4);
    }
}
