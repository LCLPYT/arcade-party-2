package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class GlowingBombMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return context.getParticipantCount() <= 12;  // maps should support respawn anchor positioning of max 12 players
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new GlowingBombInstance(gameHandle);
    }

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("glowing_bomb");
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
        return new ItemStack(Items.RESPAWN_ANCHOR);
    }
}
