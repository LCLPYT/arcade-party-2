package work.lclpnet.ap2.impl.game.team;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SimpleTeam implements Team {

    private final TeamKey key;
    private final PlayerList playerManager;
    private final Set<UUID> players = new HashSet<>();

    public SimpleTeam(TeamKey key, PlayerList playerManager) {
        this.key = key;
        this.playerManager = playerManager;
    }

    @Override
    public TeamKey key() {
        return key;
    }

    @Override
    public Set<ServerPlayer> getPlayers() {
        return players.stream()
                .map(playerManager::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void addPlayer(ServerPlayer player) {
        players.add(player.getUUID());
    }

    @Override
    public void removePlayer(ServerPlayer player) {
        players.remove(player.getUUID());
    }

    @Override
    public int getPlayerCount() {
        return players.size();
    }
}
