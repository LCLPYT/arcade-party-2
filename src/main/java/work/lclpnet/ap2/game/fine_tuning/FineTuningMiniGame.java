package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class FineTuningMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("fine_tuning");
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
        return new ItemStack(Items.NOTE_BLOCK);
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
        return new FineTuningInstance(gameHandle);
    }
}
