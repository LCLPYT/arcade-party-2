package work.lclpnet.ap2.game.panda_finder;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpamManager {

    private static final int RESET_MILLIS = 2000;
    private static final int MAX_CLICKS = 3;

    private final Map<UUID, Record> records = new HashMap<>();

    public boolean interact(ServerPlayer player) {
        Record record = records.computeIfAbsent(player.getUUID(), uuid -> new Record());
        return record.interact();
    }

    private static class Record {
        private long lastInteraction = 0L;
        private int count = 0;

        public boolean interact() {
            long before = lastInteraction;
            lastInteraction = System.currentTimeMillis();

            if (lastInteraction - before >= RESET_MILLIS) {
                count = 1;
                return false;
            }

            if (++count < MAX_CLICKS) return false;

            count = 0;
            return true;
        }
    }
}
