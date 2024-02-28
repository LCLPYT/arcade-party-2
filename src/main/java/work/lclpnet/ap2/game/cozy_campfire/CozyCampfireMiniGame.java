package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class CozyCampfireMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("cozy_campfire");
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
    public ItemStack getIcon() {
        return new ItemStack(Items.CAMPFIRE);
    }

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return context.getParticipantCount() >= 2;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new CozyCampfireInstance(gameHandle);
    }
}
