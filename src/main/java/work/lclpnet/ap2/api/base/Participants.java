package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Set;

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
}
