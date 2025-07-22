package work.lclpnet.ap2.api.game.team;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface TeamManager {

    Set<Team> getTeams();

    Optional<net.minecraft.scoreboard.Team> getMinecraftTeam(TeamKey key);

    Optional<Team> getTeam(TeamKey key);

    Optional<Team> getTeam(UUID uuid);

    void partitionIntoTeams(Set<ServerPlayerEntity> players, Set<TeamKey> teams);

    boolean isParticipating(TeamKey key);

    void setTeamEliminated(Team team);

    void bind(TeamEliminatedListener listener);

    /**
     * Whether to use team color codes.
     * This enables colored player name tags above the player model.
     * However, only a limited number of colors codes exist.
     * Set this to true, if all your team rgb colors have matching color codes (Formatting).
     */
    void setUseColorCodes(boolean useColorCodes);

    default Optional<Team> getTeam(ServerPlayerEntity player) {
        return getTeam(player.getUuid());
    }

    default void partitionIntoTeams(Participants participants, Set<TeamKey> teams) {
        partitionIntoTeams(participants.getAsSet(), teams);
    }

    default Set<net.minecraft.scoreboard.Team> getMinecraftTeams() {
        return getTeams().stream()
                .map(Team::getKey)
                .map(this::getMinecraftTeam)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    default boolean isParticipating(Team team) {
        return isParticipating(team.getKey());
    }

    default boolean isParticipating(ServerPlayerEntity player) {
        return getTeam(player).map(Team::getKey).map(this::isParticipating).orElse(false);
    }

    default Set<Team> getParticipatingTeams() {
        return getTeams().stream()
                .filter(this::isParticipating)
                .collect(Collectors.toUnmodifiableSet());
    }

    default boolean isTeamMember(ServerPlayerEntity player, Team team) {
        return getTeam(player)
                .map(t -> t.equals(team))
                .orElseGet(() -> team != null);
    }

    default boolean areTeamMates(ServerPlayerEntity first, ServerPlayerEntity second) {
        return getTeam(first)
                .map(team -> isTeamMember(second, team))
                .orElse(false);
    }
}
