package work.lclpnet.ap2.game.jump_and_run.gen;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

public record RoomInfo(BlockBox bounds, @Nullable RoomData data) {

    public RoomInfo transform(AffineIntMatrix matrix) {
        return new RoomInfo(bounds.transform(matrix), data != null ? data.transform(matrix) : null);
    }
}
