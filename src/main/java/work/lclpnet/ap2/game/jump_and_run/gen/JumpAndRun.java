package work.lclpnet.ap2.game.jump_and_run.gen;

import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.List;

public record JumpAndRun(List<JumpPart> parts, List<BlockBox> rooms, List<Checkpoint> checkpoints, BlockBox gate) {

    public int getCheckpointOffset(int room) {
        return room - 1;
    }

    public int getRoomOfCheckpoint(int checkpoint) {
        return checkpoint + 1;
    }
}
