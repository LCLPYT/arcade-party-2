package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class MazeScapeMiniGame implements MiniGame {

    @Override
    public Identifier getId() {
        return ApConstants.identifier("maze_scape");
    }

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public ItemStack getIcon(DynamicRegistryManager manager) {
        return new ItemStack(Items.SCULK_CATALYST);
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new MazeScapeInstance(gameHandle);
    }
}
