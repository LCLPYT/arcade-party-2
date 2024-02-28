package work.lclpnet.ap2.impl.game;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WinManagerAccessImpl<T, Ref extends SubjectRef> implements WinManagerAccess {

    private final WinManager<T, Ref> winManager;
    private final Function<ServerPlayerEntity, Optional<T>> mapper;

    public WinManagerAccessImpl(WinManager<T, Ref> winManager, Function<ServerPlayerEntity, Optional<T>> mapper) {
        this.winManager = winManager;
        this.mapper = mapper;
    }

    @Override
    public void draw() {
        winManager.winNobody();
    }

    @Override
    public void win(ServerPlayerEntity player) {
        mapper.apply(player).ifPresentOrElse(winManager::win, this::draw);
    }

    @Override
    public void win(Set<ServerPlayerEntity> players) {
        var winners = players.stream()
                .map(mapper)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        winManager.win(winners);
    }
}
