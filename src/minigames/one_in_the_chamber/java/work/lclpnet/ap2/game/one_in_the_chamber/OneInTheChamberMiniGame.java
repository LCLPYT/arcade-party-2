package work.lclpnet.ap2.game.one_in_the_chamber;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public @NotNull ItemStack getIcon(@NotNull RegistryAccess manager) {
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
        return new Object[] {FormatWrapper.styled(OneInTheChamberInstance.SCORE_LIMIT, ChatFormatting.YELLOW)};
    }
}
