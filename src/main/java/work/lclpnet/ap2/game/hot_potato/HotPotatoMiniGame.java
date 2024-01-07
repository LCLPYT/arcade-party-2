package work.lclpnet.ap2.game.hot_potato;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class HotPotatoMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("hot_potato");
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
        return new ItemStack(Items.BAKED_POTATO);
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
        return new HotPotatoInstance(gameHandle);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] { HotPotatoInstance.DURATION_SECONDS };
    }
}
