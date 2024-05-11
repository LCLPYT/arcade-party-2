package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public interface Participants extends Iterable<ServerPlayerEntity> {

    /**
     * @return The currently participating players.
     */
    Set<ServerPlayerEntity> getAsSet();

    void remove(ServerPlayerEntity player);

    boolean isParticipating(UUID uuid);

    @NotNull
    @Override
    default Iterator<ServerPlayerEntity> iterator() {
        return getAsSet().iterator();
    }

    default boolean isParticipating(ServerPlayerEntity player) {
        return isParticipating(player.getUuid());
    }

    default int count() {
        return getAsSet().size();
    }

    default Optional<ServerPlayerEntity> getRandomParticipant(Random random) {
        int count = count();

        if (count <= 0) {
            return Optional.empty();
        }

        return getAsSet().stream().skip(random.nextInt(count)).findFirst();
    }

    default Stream<ServerPlayerEntity> stream() {
        return getAsSet().stream();
    }
}
