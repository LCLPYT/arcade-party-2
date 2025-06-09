package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.effect.ApEffect;

import java.util.List;
import java.util.Set;

public record Segment(List<JumpPart> parts, BlockBox bounds, List<Checkpoint> checkpoints, RoomInfo roomInfo,
                      JumpRoom.Start start, BlockBox goalBounds, Set<ApEffect> effects) {

    public BlockBox gate() {
        return start.gate();
    }

    public BlockPos spawn() {
        return start.spawn();
    }

    public float spawnYaw() {
        return start.spawnYaw();
    }
}
