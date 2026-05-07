package work.lclpnet.ap2.game.eggventure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;

public class EggventureMiniGame implements MiniGame {

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
        return new EggventureInstance(gameHandle);
    }

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("eggventure");
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
    public @NotNull ItemStack getIcon(@NotNull RegistryAccess manager) {
        return manager.lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getValueOrThrow(PlayerHeads.EASTER_EGG_PINK_PATTERN)
                .createStack();
    }
}
