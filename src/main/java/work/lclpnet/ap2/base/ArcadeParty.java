package work.lclpnet.ap2.base;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.plugin.ext.KibuPlugin;
import work.lclpnet.kibu.plugin.ext.TranslatedPlugin;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.translations.loader.translation.SPITranslationLoader;
import work.lclpnet.translations.loader.translation.TranslationLoader;

import java.nio.file.Path;

public class ArcadeParty extends KibuPlugin implements TranslatedPlugin {

    public static final Logger logger = LoggerFactory.getLogger(ApConstants.ID);
    private static ArcadeParty instance;
    private TranslationService translationService = null;
    private final Path cacheDirectory = Path.of(".cache", ApConstants.ID);

    @Override
    public void loadKibuPlugin() {
        instance = this;

        logger.info("ArcadeParty2 initialized.");
    }

    @NotNull
    public static ArcadeParty getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Called to early");
        }

        return instance;
    }

    public static Identifier identifier(String path) {
        return Identifier.of(ApConstants.ID, path);
    }

    @Override
    public void injectTranslationService(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Override
    public TranslationLoader createTranslationLoader() {
        return new SPITranslationLoader(getClass().getClassLoader());
    }

    public TranslationService getTranslationService() {
        return translationService;
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }
}