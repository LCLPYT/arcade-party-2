package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.stream.Stream;

public interface DataContainer {

    void delete(ServerPlayerEntity player);

    DataEntry getEntry(ServerPlayerEntity player);

    Stream<? extends DataEntry> orderedEntries();

    void freeze();

    default Optional<ServerPlayerEntity> getBestPlayer(MinecraftServer server) {
        return orderedEntries().findFirst()
                .map(DataEntry::getPlayer)
                .map(ref -> ref.resolve(server));
    }
}
