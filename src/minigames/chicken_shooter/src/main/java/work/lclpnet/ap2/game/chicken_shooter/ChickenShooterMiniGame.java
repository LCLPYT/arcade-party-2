package work.lclpnet.ap2.game.chicken_shooter;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class ChickenShooterMiniGame implements MiniGame {

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("chicken_shooter");
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
        return new ItemStack(Items.FEATHER);
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
        return new ChickenShooterInstance(gameHandle);
    }
}
