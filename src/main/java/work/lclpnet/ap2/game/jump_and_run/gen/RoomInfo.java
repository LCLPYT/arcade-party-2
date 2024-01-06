package work.lclpnet.ap2.game.jump_and_run.gen;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;

public record RoomInfo(BlockBox bounds, @Nullable RoomData data) {


}
