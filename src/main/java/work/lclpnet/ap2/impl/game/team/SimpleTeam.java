package work.lclpnet.ap2.impl.game.team;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SimpleTeam implements Team {

    private final TeamKey key;
    private final PlayerManager playerManager;
    private final Set<UUID> players = new HashSet<>();

    public SimpleTeam(TeamKey key, PlayerManager playerManager) {
        this.key = key;
        this.playerManager = playerManager;
    }

    @Override
    public TeamKey getKey() {
        return key;
    }

    @Override
    public Set<ServerPlayerEntity> getPlayers() {
        return players.stream()
                .map(playerManager::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void addPlayer(ServerPlayerEntity player) {
        players.add(player.getUuid());
    }

    @Override
    public void removePlayer(ServerPlayerEntity player) {
        players.remove(player.getUuid());
    }

    @Override
    public int getPlayerCount() {
        return players.size();
    }
}
