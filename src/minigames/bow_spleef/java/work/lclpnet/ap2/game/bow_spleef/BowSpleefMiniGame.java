package work.lclpnet.ap2.game.bow_spleef;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class BowSpleefMiniGame implements MiniGame {

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("bow_spleef");
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
        return new ItemStack(Items.BOW);
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
        return new BowSpleefInstance(gameHandle);
    }
}
