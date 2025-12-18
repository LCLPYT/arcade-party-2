package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class TreasureHunterMinigame implements MiniGame {
    @Override
    public @NotNull ResourceLocation getId() {
        return ApConstants.identifier("treasure_hunter");
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
        return new ItemStack(Items.CHEST);
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
        return new TreasureHunterInstance(gameHandle);
    }
}
