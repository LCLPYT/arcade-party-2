package work.lclpnet.ap2.game.one_in_the_chamber;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.kibu.translate.text.FormatWrapper;

public class OneInTheChamberMiniGame implements MiniGame {
    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("one_in_the_chamber");
    }

    @Override
    public @NotNull GameType getType() {
        return GameType.FFA;
    }

    @Override
    public @NotNull String getAuthor() {
        return ApConstants.PERSON_BOPS;
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager) {
        return new ItemStack(Items.CROSSBOW);
    }

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new OneInTheChamberInstance(gameHandle);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] {OneInTheChamberInstance.SCORE_LIMIT};
    }

    @Override
    public Object[] getTaskArguments() {
        return new Object[] {FormatWrapper.styled(OneInTheChamberInstance.SCORE_LIMIT, Formatting.YELLOW)};
    }
}
