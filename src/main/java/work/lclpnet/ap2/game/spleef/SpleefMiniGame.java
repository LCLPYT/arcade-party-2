package work.lclpnet.ap2.game.spleef;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.ArcadeParty;

public class SpleefMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("spleef");
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public boolean canBeFinale() {
        return true;
    }

    @Override
    public boolean canBePlayed() {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new SpleefInstance(gameHandle);
    }
}
