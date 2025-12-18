package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;

public class PlayerRefResolver implements SubjectRefResolver<ServerPlayer, PlayerRef> {

    private final PlayerList playerManager;

    public PlayerRefResolver(PlayerList playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public @Nullable ServerPlayer resolve(PlayerRef ref) {
        return playerManager.getPlayer(ref.uuid());
    }
}
