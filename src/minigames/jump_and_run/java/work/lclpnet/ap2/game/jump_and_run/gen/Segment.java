package work.lclpnet.ap2.game.jump_and_run.gen;

import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.List;
import java.util.Set;

public record Segment(List<JumpPart> parts, BlockBox bounds, List<Checkpoint> checkpoints, RoomInfo roomInfo,
                      JumpRoom.Start start, BlockBox goalBounds, Set<ApEffect> effects) {}
