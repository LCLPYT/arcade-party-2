package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.enums.Orientation;
import net.minecraft.util.math.BlockPos;

public record WallMarker(BlockPos pos, Orientation orientation) {
}
