package work.lclpnet.ap2.game.eggventure;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;

public class EggventureMiniGame implements MiniGame {

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
        return new EggventureInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("eggventure");
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
        return manager.getOrThrow(ApRegistries.PLAYER_HEAD)
                .getValueOrThrow(PlayerHeads.EASTER_EGG_PINK_PATTERN)
                .createStack();
    }
}
