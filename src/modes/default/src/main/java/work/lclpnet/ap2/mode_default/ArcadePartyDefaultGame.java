package work.lclpnet.ap2.mode_default;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.impl.bootstrap.ApDataPacks;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.game.api.Game;
import work.lclpnet.game.api.GameConfig;
import work.lclpnet.game.api.GameFactory;
import work.lclpnet.game.api.data.GameDataPacks;
import work.lclpnet.game.api.start.GameStartScope;
import work.lclpnet.game.api.start.GameStatusManager;
import work.lclpnet.game.impl.MinecraftGameConfig;
import work.lclpnet.game.util.GameStartUtil;

import java.nio.file.Path;

public class ArcadePartyDefaultGame implements Game {

    private final Path cacheDirectory = Path.of(".cache", ApConstants.ID);

    @Override
    public @NonNull GameConfig getConfig() {
        return new MinecraftGameConfig(ApConstants.ID, new ItemStackTemplate(Items.GOLD_BLOCK));
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartScope scope) {
        return scope.playerCount() >= getMinRequiredPlayers();
    }

    @Override
    public void configureStatusManager(@NonNull GameStatusManager manager) {
        GameStartUtil.configureNotEnoughPlayersMessage(manager, getMinRequiredPlayers());
        GameStartUtil.configureWaitingForPlayersBossBar(manager);
    }

    private int getMinRequiredPlayers() {
        if (ApConstants.DEVELOPMENT) return 1;

        return 2;
    }

    @Override
    public @NonNull GameDataPacks getBootstrapDataPacks() {
        return new ApDataPacks(cacheDirectory, CONFIG_FACTORY, ApConstants.logger);
    }

    @Override
    public @NonNull GameFactory createFactory() {
        return new ArcadePartyFactory(CONFIG_FACTORY, ApConstants.logger);
    }

    public static final JsonConfigFactory<Ap2Config> CONFIG_FACTORY = new JsonConfigFactory<>() {
        @Override
        public Ap2Config createDefaultConfig() {
            return new Ap2Config();
        }

        @Override
        public Ap2Config createConfig(JSONObject json) {
            return new Ap2Config(json);
        }
    };
}
