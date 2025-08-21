package work.lclpnet.ap2.host_default;

import net.minecraft.MinecraftVersion;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.impl.i18n.VanillaTranslations;
import work.lclpnet.ap2.impl.util.AssetManager;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.kibu.translate.util.ModTranslations;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameFactory;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.translations.loader.MultiTranslationLoader;
import work.lclpnet.translations.loader.TranslationLoader;

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

        return loader;
    }

    @Override
    public GameInstance createInstance(GameEnvironment gameEnvironment) {
        return new ArcadePartyInstance(gameEnvironment, vanillaTranslations, configFactory, logger);
    }
}
