package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MovementTracker {

    // last tracked position will be MIN_TRACK_DISTANCE * MAX_POSITIONS blocks away
    private static final double MIN_TRACK_DISTANCE = 3.0;
    private static final int MAX_POSITIONS = 5;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final BlockBox goal;

    public MovementTracker(BlockBox goal) {
        this.goal = goal;
    }

    public void track(ServerPlayer player) {
        getEntry(player).update(player.position());
    }

    @Nullable
    public Vec3 getMostDistantPos(ServerPlayer player) {
        return getEntry(player).getMostDistantPos();
    }

    @NotNull
    private Entry getEntry(ServerPlayer player) {
        return entries.computeIfAbsent(player.getUUID(), uuid -> new Entry());
    }

    private class Entry {
        private final Vec3[] positions = new Vec3[MAX_POSITIONS];
        private int pointer = -1;
        private int tracked = 0;

        public void update(Vec3 pos) {
            Vec3 lastPos = getLastPos();

            if (lastPos == null) {
                track(pos);
                return;
            }

            // the position must be at least MIN_TRACK_DISTANCE away from the last position
            if (lastPos.distanceToSqr(pos) < MIN_TRACK_DISTANCE * MIN_TRACK_DISTANCE) return;

            // the new position must also be closer to the goal than the last position
            if (goal.squaredDistanceTo(pos) >= goal.squaredDistanceTo(lastPos)) return;

            track(pos);
        }

        private void track(Vec3 pos) {
            pointer = (pointer + 1) % MAX_POSITIONS;
            positions[pointer] = pos;
            tracked = Math.min(tracked + 1, MAX_POSITIONS);
        }

        @Nullable
        private Vec3 getLastPos() {
            if (pointer == -1) {
                return null;
            }

            return positions[pointer];
        }

        @Nullable
        public Vec3 getMostDistantPos() {
            if (tracked == 0) {
                return null;
            }

            if (tracked == MAX_POSITIONS) {
                return positions[(pointer + 1) % MAX_POSITIONS];
            }

            return positions[0];
        }
    }
}
