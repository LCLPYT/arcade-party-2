package work.lclpnet.ap2.game.aim_master;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class AimMasterMiniGame implements MiniGame {
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
        return new AimMasterInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("aim_master");
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
        return new ItemStack(Items.TARGET);
    }

}
