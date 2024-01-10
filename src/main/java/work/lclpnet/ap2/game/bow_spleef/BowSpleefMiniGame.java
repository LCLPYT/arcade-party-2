package work.lclpnet.ap2.game.bow_spleef;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class BowSpleefMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("bow_spleef");
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_BOPS;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.BOW);
    }

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new BowSpleefInstance(gameHandle);
    }
}
