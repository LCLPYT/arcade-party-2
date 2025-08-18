package work.lclpnet.ap2.impl.game;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class TestMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ApConstants.identifier("test");
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
    public ItemStack getIcon(DynamicRegistryManager manager) {
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
