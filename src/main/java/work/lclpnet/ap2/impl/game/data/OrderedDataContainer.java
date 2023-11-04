package work.lclpnet.ap2.impl.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.ap2.impl.game.data.entry.SimpleDataEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class OrderedDataContainer implements DataContainer {

    private final List<PlayerRef> order = new ArrayList<>();
    private boolean frozen = false;

    public void add(ServerPlayerEntity player) {
        synchronized (this) {
            if (frozen) return;

            PlayerRef ref = PlayerRef.create(player);

            if (order.contains(ref)) return;

            order.add(ref);
        }
    }

    @Override
    public void delete(ServerPlayerEntity player) {
        synchronized (this) {
            if (frozen) return;

            order.remove(PlayerRef.create(player));  // O(n) should not really be an issue
        }
    }

    @Override
    public DataEntry getEntry(ServerPlayerEntity player) {
        return new SimpleDataEntry(PlayerRef.create(player));
    }

    @Override
    public Stream<? extends DataEntry> orderedEntries() {
        return order.stream().map(SimpleDataEntry::new);
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }
}
