package work.lclpnet.ap2.game.jump_and_run.gen;

import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

import java.util.List;

public record RoomData(float value, JumpAssistance assistance, List<Checkpoint> checkpoints) {

    public RoomData transform(AffineIntMatrix mat4) {
        var transformedAssistance = assistance.transform(mat4);

        var transformedCheckpoints = checkpoints.stream()
                .map(checkpoint -> checkpoint.transform(mat4))
                .toList();

        return new RoomData(value, transformedAssistance, transformedCheckpoints);
    }
}
