package work.lclpnet.ap2.impl.i18n;

import net.minecraft.MinecraftVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.impl.util.AssetManager;
import work.lclpnet.translations.DefaultLanguageTranslator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VanillaTranslationsTest {

    private static final Logger logger = LoggerFactory.getLogger(VanillaTranslationsTest.class);
    private static AssetManager assetManager;
    private VanillaTranslations translations;
    private DefaultLanguageTranslator translator;

    @BeforeAll
    static void setUpAll() throws IOException {
        String version = MinecraftVersion.CURRENT.getName();
        Path assetsRoot = Files.createTempDirectory("vt_assets");

        // TODO remove network dependency by using a local state of required assets
        assetManager = new AssetManager(assetsRoot, version, logger);

        // try to clean up on exit
        assetsRoot.toFile().deleteOnExit();
    }

    @BeforeEach
    void setUp() {
        translations = new VanillaTranslations(assetManager, logger);

        var loader = translations.getTranslationLoader();

        translator = new DefaultLanguageTranslator(loader);
    }

    @Test
    void init_default() {
        translations.init();

        translator.reload().join();

        assertEquals("Dirt", translator.translate("en_us", "block.minecraft.dirt"));
    }

    @Test
    void translator_notInitialized() {
        translator.reload().join();

        assertEquals("block.minecraft.dirt", translator.translate("en_us", "block.minecraft.dirt"));
    }

    @Test
    void loadLanguage_notInitialized() {
        assertFalse(translations.loadLanguage("de_de"));
        assertFalse(translations.loadLanguage("ja_jp"));

        translator.reload().join();

        assertEquals("block.minecraft.dirt", translator.translate("de_de", "block.minecraft.dirt"));
        assertEquals("block.minecraft.dirt", translator.translate("ja_jp", "block.minecraft.dirt"));
    }

    @Test
    void loadLanguage_initialized() {
        translations.init();

        assertTrue(translations.loadLanguage("de_de"));
        assertTrue(translations.loadLanguage("ja_jp"));

        translator.reload().join();

        assertEquals("Erde", translator.translate("de_de", "block.minecraft.dirt"));
        assertEquals("土", translator.translate("ja_jp", "block.minecraft.dirt"));

        translations.unloadLanguage("ja_jp");

        translator.reload().join();

        assertEquals("Dirt", translator.translate("ja_jp", "block.minecraft.dirt"));
    }

    @Test
    void unloadLanguage_translationsRemoved() {
        translations.init();

        assertTrue(translations.loadLanguage("de_de"));
        assertTrue(translations.loadLanguage("ja_jp"));

        translator.reload().join();

        assertTrue(translations.unloadLanguage("ja_jp"));
        assertFalse(translations.unloadLanguage("ja_jp"));  // unloading twice shouldn't do anything

        translator.reload().join();

        assertEquals("Erde", translator.translate("de_de", "block.minecraft.dirt"));
        // should fall back to default language en_us
        assertEquals("Dirt", translator.translate("ja_jp", "block.minecraft.dirt"));
    }

    @Test
    void translator_withPredicate_partialTranslationsLoaded() {
        // only load death translations
        translations = new VanillaTranslations(assetManager, logger, translationKey -> translationKey.startsWith("death."));

        var loader = translations.getTranslationLoader();

        translator = new DefaultLanguageTranslator(loader);

        translations.init();

        assertTrue(translations.loadLanguage("de_de"));
        assertTrue(translations.loadLanguage("ja_jp"));

        translator.reload().join();

        // dirt block translation shouldn't have been loaded, as the predicate determines
        assertEquals("block.minecraft.dirt", translator.translate("en_us", "block.minecraft.dirt"));
        assertEquals("block.minecraft.dirt", translator.translate("de_de", "block.minecraft.dirt"));
        assertEquals("block.minecraft.dirt", translator.translate("ja_jp", "block.minecraft.dirt"));

        // translations matching the predicate should have been loaded
        assertEquals("%1$s fell from a high place", translator.translate("en_us", "death.fell.accident.generic"));
        assertEquals("%1$s fiel aus zu großer Höhe", translator.translate("de_de", "death.fell.accident.generic"));
        assertEquals("%1$sは高い所から落ちた", translator.translate("ja_jp", "death.fell.accident.generic"));
    }
}