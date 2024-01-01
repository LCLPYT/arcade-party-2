package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.Collider;

public record Checkpoint(BlockPos spawn, float yaw, Collider bounds) {

}
