package work.lclpnet.ap2.game.jump_and_run.gen;

import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;

import java.util.List;

public record Segment(List<JumpPart> parts, BlockBox bounds, List<Checkpoint> checkpoints, BlockBox gate) {
}
