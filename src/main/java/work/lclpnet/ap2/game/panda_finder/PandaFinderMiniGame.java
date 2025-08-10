package work.lclpnet.ap2.game.panda_finder;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.kibu.translate.text.FormatWrapper;

public class PandaFinderMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ApConstants.identifier("panda_finder");
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
    public ItemStack getIcon(DynamicRegistryManager manager) {
        return new ItemStack(Items.BAMBOO);
    }

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
        return new PandaFinderInstance(gameHandle);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] { FormatWrapper.styled(PandaFinderInstance.WIN_SCORE, Formatting.YELLOW) };
    }
}
