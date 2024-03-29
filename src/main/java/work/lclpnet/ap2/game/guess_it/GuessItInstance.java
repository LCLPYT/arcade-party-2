package work.lclpnet.ap2.game.guess_it;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

public class GuessItInstance extends DefaultGameInstance {

    public GuessItInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void ready() {

    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return null;
    }
}
