package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.enums.Orientation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

public record Connector3(BlockPos pos, Orientation orientation) {

    public Vec3i direction() {
        return orientation.getFacing().getVector();
    }

    public int rotateToFace(Connector3 other) {
        int rotation = this.orientation.getFacing().getHorizontal();
        int otherRotation = other.orientation.getFacing().getHorizontal();

        return Math.floorMod(rotation - otherRotation + 2, 4);
    }
}
