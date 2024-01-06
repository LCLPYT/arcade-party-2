package work.lclpnet.ap2.game.jump_and_run.gen;

import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.List;

public record JumpAndRun(List<JumpPart> parts, List<RoomInfo> rooms, List<Checkpoint> checkpoints, BlockBox gate) {

    public int getCheckpointOffset(final int room) {
        final int maxRoom = Math.min(room, rooms.size());
        int intermediate = 0;

        for (int i = 0; i < maxRoom; i++) {
            RoomInfo info = rooms.get(i);
            RoomData data = info.data();

            if (data == null) continue;

            intermediate += data.checkpoints().size();
        }

        return room - 1 + intermediate;
    }

    public int getRoomOfCheckpoint(final int checkpoint) {
        int idx = 0;

        for (int room = 1; room < rooms.size(); room++) {
            RoomInfo info = rooms.get(room);
            RoomData data = info.data();

            if (data != null) {
                idx += data.checkpoints().size();
            }

            if (idx >= checkpoint) {
                return room;
            }

            idx++;
        }

        return 1;  // fallback to first room
    }
}
