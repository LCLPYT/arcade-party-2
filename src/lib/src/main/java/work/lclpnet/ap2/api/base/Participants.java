package work.lclpnet.ap2.api.base;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public interface Participants extends Iterable<ServerPlayer> {

    /**
     * @return The currently participating players.
     */
    Set<ServerPlayer> getAsSet();

    void remove(ServerPlayer player);

    boolean isParticipating(UUID uuid);

    @NotNull
    @Override
    default Iterator<ServerPlayer> iterator() {
        return getAsSet().iterator();
    }

    default boolean isParticipating(ServerPlayer player) {
        return isParticipating(player.getUUID());
    }

    default int count() {
        return getAsSet().size();
    }

    default Optional<ServerPlayer> getRandomParticipant(Random random) {
        int count = count();

        if (count <= 0) {
            return Optional.empty();
        }

        return stream().skip(random.nextInt(count)).findFirst();
    }

    default Optional<ServerPlayer> getParticipant(UUID uuid) {
        return stream()
                .filter(player -> player.getUUID().equals(uuid))
                .findAny();
    }

    default Stream<ServerPlayer> stream() {
        return getAsSet().stream();
    }
}
