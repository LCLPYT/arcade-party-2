package work.lclpnet.ap2.game.mimicry;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class MimicryMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new MimicryInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("mimicry");
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
        return new ItemStack(Items.ACACIA_BUTTON);
    }
}
