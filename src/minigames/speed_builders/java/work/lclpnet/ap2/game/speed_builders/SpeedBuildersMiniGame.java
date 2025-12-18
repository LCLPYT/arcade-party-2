package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class SpeedBuildersMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new SpeedBuildersInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ApConstants.identifier("speed_builders");
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
    public ItemStack getIcon(RegistryAccess manager) {
        return new ItemStack(Items.BRICKS);
    }
}
