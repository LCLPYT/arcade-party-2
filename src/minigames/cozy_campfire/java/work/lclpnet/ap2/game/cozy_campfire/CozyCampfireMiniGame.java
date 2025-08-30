package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class CozyCampfireMiniGame implements MiniGame {

    private static final boolean DEBUG_PLAYER_CONSTRAINT = false;

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("cozy_campfire");
    }

    @Override
    public @NotNull GameType getType() {
        return GameType.TEAM;
    }

    @Override
    public @NotNull String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager) {
        return new ItemStack(Items.CAMPFIRE);
    }

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        int count = context.getParticipantCount();

        if (DEBUG_PLAYER_CONSTRAINT) {
            return count >= 2;
        }

        return count == 2 || count >= 4;
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new CozyCampfireInstance(gameHandle);
    }
}
