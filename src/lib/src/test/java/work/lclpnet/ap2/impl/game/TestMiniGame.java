package work.lclpnet.ap2.impl.game;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.game.MiniGame;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.MiniGameInstance;

public class TestMiniGame implements MiniGame {

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("test");
    }

    @Override
    public @NotNull GameType getType() {
        return GameType.FFA;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Dev";
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull RegistryAccess manager) {
        return new ItemStack(Items.EMERALD);
    }

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return false;
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        throw new UnsupportedOperationException();
    }
}
