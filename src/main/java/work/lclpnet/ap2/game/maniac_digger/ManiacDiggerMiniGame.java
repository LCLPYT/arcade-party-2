package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class ManiacDiggerMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return context.getParticipantCount() <= 12;  // maps should support 12 pipes
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new ManiacDiggerInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("maniac_digger");
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
        return new ItemStack(Items.GOLDEN_SHOVEL);
    }
}
