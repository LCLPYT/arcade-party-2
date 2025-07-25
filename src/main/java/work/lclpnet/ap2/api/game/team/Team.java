package work.lclpnet.ap2.api.game.team;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;

import java.util.Set;
import java.util.stream.Collectors;

public interface Team extends TeamKeyable {

    /**
     * Get all online players in this team.
     * Those players may or may not be participating.
     * @return A set of players in this team. Modifications on the set do not affect the actual team members.
     */
    Set<ServerPlayerEntity> getPlayers();

    void addPlayer(ServerPlayerEntity player);

    void removePlayer(ServerPlayerEntity player);

    /**
     * Get the total amount of players in this team.
     * This is not necessarily equal to <code>getPlayers().size()</code>, as offline players are also counted.
     * @return The total amount of players, online or offline, in this team.
     */
    int getPlayerCount();

    /**
     * Get all participating players in this team.
     * @param participants The {@link Participants} manager.
     * @return A set of participants of this team. Modifications on the set do not affect the actual team participants.
     */
    default Set<ServerPlayerEntity> getParticipatingPlayers(Participants participants) {
        return getPlayers().stream()
                .filter(participants::isParticipating)
                .collect(Collectors.toUnmodifiableSet());
    }
}
