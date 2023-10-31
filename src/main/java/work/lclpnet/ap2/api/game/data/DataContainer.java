package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.stream.Stream;

public interface DataContainer {

    void delete(ServerPlayerEntity player);

    DataEntry getEntry(ServerPlayerEntity player);

    Stream<? extends DataEntry> orderedEntries();
}