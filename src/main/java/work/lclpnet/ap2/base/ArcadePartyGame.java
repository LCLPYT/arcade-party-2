package work.lclpnet.ap2.base;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import work.lclpnet.lobby.game.Game;
import work.lclpnet.lobby.game.GameEnvironment;
import work.lclpnet.lobby.game.GameInstance;
import work.lclpnet.lobby.game.conf.GameConfig;
import work.lclpnet.lobby.game.conf.MinecraftGameConfig;

public class ArcadePartyGame implements Game {

    private final MinecraftGameConfig config;

    public ArcadePartyGame() {
        config = new MinecraftGameConfig("ap2", "Arcade Party", new ItemStack(Items.GOLD_BLOCK));
    }

    @Override
    public GameConfig getConfig() {
        return config;
    }

    @Override
    public GameInstance createInstance(GameEnvironment gameEnvironment) {
        return new ArcadePartyGameInstance(gameEnvironment);
    }
}
