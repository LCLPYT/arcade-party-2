package work.lclpnet.ap2.game.paintball;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class PaintballMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return context.getParticipantCount() % 2 == 0;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new PaintballInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("paintball");
    }

    @Override
    public GameType getType() {
        return GameType.TEAM;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public ItemStack getIcon(DynamicRegistryManager manager) {
        return new ItemStack(Items.IRON_HORSE_ARMOR);
    }
}
