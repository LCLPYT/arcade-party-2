package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.GameWinners;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerGameWinners implements GameWinners<PlayerRef> {

    private final Set<ServerPlayerEntity> players;
    private final Set<PlayerRef> refs;

    public PlayerGameWinners(Set<ServerPlayerEntity> players) {
        this.players = Collections.unmodifiableSet(players);
        this.refs = players.stream()
                .map(PlayerRef::create)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<ServerPlayerEntity> getPlayers() {
        return players;
    }

    @Override
    public Set<PlayerRef> getSubjects() {
        return refs;
    }
}
