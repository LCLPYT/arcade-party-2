package work.lclpnet.ap2.base;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.impl.bootstrap.ApDataPacks;
import work.lclpnet.kibu.translate.util.ModTranslations;
import work.lclpnet.lobby.game.api.Game;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.lobby.game.api.TranslatedGame;
import work.lclpnet.lobby.game.api.data.GameDataPacks;
import work.lclpnet.lobby.game.conf.GameConfig;
import work.lclpnet.lobby.game.conf.MinecraftGameConfig;
import work.lclpnet.translations.loader.translation.TranslationLoader;

import java.nio.file.Path;

public class ArcadeParty implements Game, TranslatedGame {

    public static final Logger logger = LoggerFactory.getLogger(ApConstants.ID);
    private final MinecraftGameConfig config = new MinecraftGameConfig("ap2", new ItemStack(Items.GOLD_BLOCK));
    private final Path cacheDirectory = Path.of(".cache", ApConstants.ID);

    public static Identifier identifier(String path) {
        return Identifier.of(ApConstants.ID, path);
    }

    @Override
    public GameConfig getConfig() {
        return config;
    }

    @Override
    public GameInstance createInstance(GameEnvironment gameEnvironment) {
        return new ArcadePartyInstance(gameEnvironment, cacheDirectory, logger);
    }

    @Override
    public GameDataPacks getBootstrapDataPacks() {
        return new ApDataPacks(cacheDirectory, logger);
    }

    @Override
    public TranslationLoader getTranslationLoader() {
        return ModTranslations.assetTranslationLoader(ApConstants.ID, logger);
    }
}
