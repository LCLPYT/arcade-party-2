package work.lclpnet.ap2.mode_default;

import net.minecraft.MinecraftVersion;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.impl.base.FabricMiniGameManager;
import work.lclpnet.ap2.impl.i18n.VanillaTranslations;
import work.lclpnet.ap2.impl.util.AssetManager;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.kibu.translate.util.ModTranslations;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameFactory;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.translations.loader.MultiTranslationLoader;
import work.lclpnet.translations.loader.TranslationLoader;
import work.lclpnet.translations.loader.UrlArchiveTranslationLoader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

public class ArcadePartyFactory implements GameFactory {

    private final JsonConfigFactory<Ap2Config> configFactory;
    private final Logger logger;
    private VanillaTranslations vanillaTranslations = null;

    public ArcadePartyFactory(JsonConfigFactory<Ap2Config> configFactory, Logger logger) {
        this.configFactory = configFactory;
        this.logger = logger;
    }

    @Override
    public TranslationLoader createTranslationLoader() {
        MultiTranslationLoader loader = new MultiTranslationLoader();

        // load translations from assets
        var assetLoader = ModTranslations.assetTranslationLoader(ApConstants.LIB_ID, ApConstants.ID, logger);
        loader.addLoader(assetLoader);

        // also load vanilla death messages (unavailable until initialized)
        var assetManager = AssetManager.getShared(MinecraftVersion.CURRENT.name());
        vanillaTranslations = new VanillaTranslations(assetManager, logger, translationKey -> translationKey.startsWith("death."));
        loader.addLoader(vanillaTranslations.getTranslationLoader());

        for (var source : new FabricMiniGameManager(logger).getGameSources()) {
            var urls = source.rootPaths().stream()
                    .map(path -> {
                        try {
                            return path.toUri().toURL();
                        } catch (MalformedURLException e) {
                            logger.error("Failed to convert path {} to url", path, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(URL[]::new);

            var miniGameTranslations = UrlArchiveTranslationLoader.ofJson(urls, List.of("lang/"), logger);

            loader.addLoader(miniGameTranslations);
        }

        return loader;
    }

    @Override
    public GameInstance createInstance(GameEnvironment gameEnvironment) {
        return new ArcadePartyInstance(gameEnvironment, vanillaTranslations, configFactory, logger);
    }
}
