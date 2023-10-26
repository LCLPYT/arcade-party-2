package work.lclpnet.ap2.impl.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.ap2.impl.game.data.entry.SimpleDataEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A data container for last one standing game modes.
 */
public class EliminationDataContainer implements DataContainer {

    private final List<PlayerRef> list = new ArrayList<>();

    public void eliminated(ServerPlayerEntity player) {
        synchronized (this) {
            list.add(0, PlayerRef.create(player));
        }
    }

    @Override
    public void delete(ServerPlayerEntity player) {
        synchronized (this) {
            list.remove(PlayerRef.create(player));  // O(n) should not really be an issue
        }
    }

    @Override
    public DataEntry getEntry(ServerPlayerEntity player) {
        return new SimpleDataEntry(PlayerRef.create(player));
    }

    @Override
    public Stream<DataEntry> orderedEntries() {
        return list.stream().map(SimpleDataEntry::new);
    }
}
