package work.lclpnet.ap2.impl.game;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
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
    public ItemStack getIcon() {
        return new ItemStack(Items.EMERALD);
    }

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return false;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return null;
    }
}
