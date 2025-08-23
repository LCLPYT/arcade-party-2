package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class JumpAndRunMiniGame implements MiniGame {

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("jump_and_run");
    }

    @Override
    public @NotNull GameType getType() {
        return GameType.FFA;
    }

    @Override
    public @NotNull String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager) {
        return new ItemStack(Items.DIAMOND_BOOTS);
    }

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new JumpAndRunInstance(gameHandle);
    }
}
