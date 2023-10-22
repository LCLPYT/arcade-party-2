package work.lclpnet.ap2.impl.game;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.ArcadeParty;

public class TestMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("test");
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public String getAuthor() {
        return "Dev";
    }

    @Override
    public boolean canBeFinale() {
        return false;
    }

    @Override
    public boolean canBePlayed() {
        return false;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return null;
    }
}
