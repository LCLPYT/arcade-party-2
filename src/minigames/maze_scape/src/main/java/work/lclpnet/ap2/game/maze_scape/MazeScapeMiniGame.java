package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.GameType;
import work.lclpnet.ap2.game.MapLevelGameFactory;
import work.lclpnet.ap2.game.MiniGame;
import work.lclpnet.ap2.game.MiniGameFactory;
import work.lclpnet.ap2.game.MiniGameHandle;

public class MazeScapeMiniGame implements MiniGame {

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("maze_scape");
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
    public @NotNull GameType getType() {
        return GameType.FFA;
    }

    @Override
    public @NotNull String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull RegistryAccess manager) {
        return new ItemStack(Items.SCULK_CATALYST);
    }

    @Override
    public @NotNull MiniGameFactory createFactory(@NotNull MiniGameHandle gameHandle) {
        return new MapLevelGameFactory(MazeScapeInstance::new);
    }
}
