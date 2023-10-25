package work.lclpnet.ap2.impl.game;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.lobby.game.util.ProtectorUtils;

public abstract class DefaultGameInstance implements MiniGameInstance {

    protected final MiniGameHandle gameHandle;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    @Override
    public void start() {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, this::onReady);
    }

    protected abstract void onReady();
}
