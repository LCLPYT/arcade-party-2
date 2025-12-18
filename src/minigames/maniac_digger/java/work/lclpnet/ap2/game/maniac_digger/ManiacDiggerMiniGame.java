package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class ManiacDiggerMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return context.getParticipantCount() <= 12;  // maps should support 12 pipes
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new ManiacDiggerInstance(gameHandle);
    }

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("maniac_digger");
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
        return new ItemStack(Items.GOLDEN_SHOVEL);
    }
}
