package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;

public class PlayerRefResolver implements SubjectRefResolver<ServerPlayerEntity, PlayerRef> {

    private final PlayerManager playerManager;

    public PlayerRefResolver(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public @Nullable ServerPlayerEntity resolve(PlayerRef ref) {
        return playerManager.getPlayer(ref.uuid());
    }
}
