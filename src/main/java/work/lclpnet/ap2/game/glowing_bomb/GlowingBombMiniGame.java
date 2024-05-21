package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class GlowingBombMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return context.getParticipantCount() <= 12;  // maps should support respawn anchor positioning of max 12 players
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new GlowingBombInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("glowing_bomb");
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
        return new ItemStack(Items.RESPAWN_ANCHOR);
    }
}
