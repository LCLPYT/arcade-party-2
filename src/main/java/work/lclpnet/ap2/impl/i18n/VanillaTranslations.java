package work.lclpnet.ap2.impl.i18n;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.AssetManager;
import work.lclpnet.translations.loader.CachedMultiTranslationLoader;
import work.lclpnet.translations.loader.TranslationLoader;
import work.lclpnet.translations.loader.UrlArchiveTranslationLoader;
import work.lclpnet.translations.loader.UrlTranslationLoader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class VanillaTranslations {

    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("[a-z_]+");
    private final AssetManager assetManager;
    private final Logger logger;
    private final @Nullable Predicate<String> translationKeyPredicate;
    private final Set<String> availableLanguages = new HashSet<>();
    private final Map<String, TranslationLoader> languageLoaders = new HashMap<>();
    private final CachedMultiTranslationLoader parentLoader = new CachedMultiTranslationLoader();
    private volatile boolean initialized = false;

    public VanillaTranslations(AssetManager assetManager, Logger logger) {
        this(assetManager, logger, null);
    }

    public VanillaTranslations(AssetManager assetManager, Logger logger, @Nullable Predicate<String> translationKeyPredicate) {
        this.assetManager = assetManager;
        this.logger = logger;
        this.translationKeyPredicate = translationKeyPredicate;
    }

    @Blocking
    public synchronized void init() {
        if (initialized) return;

        initialized = true;

        // default language (en_us) is located in the client jar file
        try {
            var defaultLanguageLoader = getDefaultLanguageLoader();

            if (defaultLanguageLoader != null) {
                parentLoader.add(defaultLanguageLoader);
            }
        } catch (IOException e) {
            logger.error("Failed to download default language", e);
        }

        // Other languages are listed in the pack.mcmeta asset
        // Those languages can be loaded on demand later
        try {
            downloadLanguageIndex();
        } catch (IOException e) {
            logger.error("Failed to download language index");
        }
    }

    @Blocking
    public synchronized boolean addLanguage(String language) {
        if (!availableLanguages.contains(language)) {
            return false;
        }

        // check if already loaded
        if (languageLoaders.containsKey(language)) {
            return false;
        }

        TranslationLoader loader = getLanguageLoader(language);

        if (loader == null) {
            return false;
        }

        TranslationLoader oldLoader = languageLoaders.put(language, loader);

        if (oldLoader != null) {
            parentLoader.remove(oldLoader);
        }

        parentLoader.add(loader);

        return true;
    }

    @Blocking
    public synchronized boolean removeLanguage(String language) {
        TranslationLoader loader = languageLoaders.remove(language);

        if (loader == null) {
            return false;
        }

        parentLoader.remove(loader);

        return true;
    }

    public boolean hasLanguage(String language) {
        return initialized && ("en_us".equals(language) || languageLoaders.containsKey(language));
    }

    @Nullable
    private TranslationLoader getLanguageLoader(String language) {
        if (!LANGUAGE_PATTERN.matcher(language).matches()) {
            // invalid language string
            return null;
        }

        Path path = assetManager.getAsset("minecraft/lang/%s.json".formatted(language));

        if (path == null) {
            // no translations for that language
            return null;
        }

        URL url;

        try {
            url = path.toUri().toURL();
        } catch (MalformedURLException e) {
            logger.error("Could not construct url for {}", path, e);
            return null;
        }

        if (translationKeyPredicate != null) {
            return new UrlTranslationLoader(url, language, logger, this::createPredicateParser);
        }

        return UrlTranslationLoader.ofJson(url, language, logger);
    }

    @Nullable
    private TranslationLoader getDefaultLanguageLoader() throws IOException {
        Path clientJar = assetManager.getDownload(AssetManager.DOWNLOAD_CLIENT);

        if (clientJar == null) {
            logger.error("Failed to download client jar file");
            return null;
        }

        var src = UrlArchiveTranslationLoader.getResourceLocations(clientJar);
        var dirs = List.of("assets/minecraft/lang");

        if (translationKeyPredicate != null) {
            return new UrlArchiveTranslationLoader(src, dirs, logger,
                    this::createPredicateParser,
                    file -> file.endsWith(".json"));
        }

        return UrlArchiveTranslationLoader.ofJson(src, dirs, logger);
    }

    private PartialJsonTranslationParser createPredicateParser() {
        return new PartialJsonTranslationParser(translationKeyPredicate);
    }

    private void downloadLanguageIndex() throws IOException {
        Path metaPath = assetManager.getAsset("pack.mcmeta");

        if (metaPath == null) {
            logger.error("Failed to find pack.mcmeta asset");
            return;
        }

        String metaJson = Files.readString(metaPath, StandardCharsets.UTF_8);
        JSONObject meta = new JSONObject(metaJson);

        JSONObject languages = meta.optJSONObject("language");

        if (languages == null) {
            logger.warn("There are no languages defined in pack.mcmeta (key 'language')");
            return;
        }

        for (String language : languages.keySet()) {
            if ("en_us".equals(language)) continue;

            availableLanguages.add(language);
        }
    }

    public TranslationLoader getTranslationLoader() {
        return parentLoader;
    }
}
