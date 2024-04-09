package work.lclpnet.ap2.game.guess_it;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class GuessItMiniGame implements MiniGame {
    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("guess_it");
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;  // multiple players can have the same score
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new GuessItInstance(gameHandle);
    }
}
