package work.lclpnet.ap2.game.pig_race;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class PigRaceMiniGame implements MiniGame {

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
        return new PigRaceInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ApConstants.identifier("pig_race");
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
        return new ItemStack(Items.CARROT_ON_A_STICK);
    }
}
