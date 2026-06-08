package work.lclpnet.ap2.game.guess_it;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.game.MiniGame;
import work.lclpnet.ap2.game.MiniGameFactory;
import work.lclpnet.ap2.game.util.MapLevelGameFactory;

public class GuessItMiniGame implements MiniGame {
    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("guess_it");
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
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return false;  // multiple players can have the same score
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public @NotNull MiniGameFactory createFactory() {
        return new MapLevelGameFactory(GuessItInstance::new);
    }
}
