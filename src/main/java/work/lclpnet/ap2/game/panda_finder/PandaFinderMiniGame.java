package work.lclpnet.ap2.game.panda_finder;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.kibu.translate.text.FormatWrapper;

public class PandaFinderMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("panda_finder");
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
        return new ItemStack(Items.BAMBOO);
    }

    @Override
    public boolean canBeFinale() {
        return false;
    }

    @Override
    public boolean canBePlayed() {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new PandaFinderInstance(gameHandle);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] {FormatWrapper.styled(PandaFinderInstance.MAX_SCORE, Formatting.YELLOW)};
    }
}
