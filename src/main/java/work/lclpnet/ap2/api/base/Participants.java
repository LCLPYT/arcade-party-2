package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

public interface Participants extends Iterable<ServerPlayerEntity> {

    /**
     * @return The currently participating players.
     */
    Set<ServerPlayerEntity> getAsSet();

    void remove(ServerPlayerEntity player);

    @NotNull
    @Override
    default Iterator<ServerPlayerEntity> iterator() {
        return getAsSet().iterator();
    }

    default boolean isParticipating(ServerPlayerEntity player) {
        return getAsSet().contains(player);
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
