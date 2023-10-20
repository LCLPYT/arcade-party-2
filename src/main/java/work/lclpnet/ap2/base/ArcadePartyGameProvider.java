package work.lclpnet.ap2.base;

import work.lclpnet.lobby.game.api.Game;
import work.lclpnet.lobby.game.api.GameProvider;

public class ArcadePartyGameProvider implements GameProvider {

    @Override
    public Game provideGame() {
        return new ArcadePartyGame();
    }
}
