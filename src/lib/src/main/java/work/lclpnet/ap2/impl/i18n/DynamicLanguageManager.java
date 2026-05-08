package work.lclpnet.ap2.impl.i18n;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.VisibleForTesting;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class DynamicLanguageManager {

    private final VanillaTranslations translations;
    private final Function<ServerPlayer, String> languageGetter;
    private final Runnable updateCallback;
    private final Map<UUID, String> playerLanguage = new HashMap<>();
    @VisibleForTesting final Object2IntMap<String> languageUserCount;

    public DynamicLanguageManager(VanillaTranslations translations, Function<ServerPlayer, String> languageGetter,
                                  Runnable updateCallback) {
        this.translations = translations;
        this.languageGetter = languageGetter;
        this.updateCallback = updateCallback;

        languageUserCount = new Object2IntOpenHashMap<>();
        languageUserCount.defaultReturnValue(0);
    }

    public void init(HookRegistrar hooks, Iterable<ServerPlayer> allPlayers) {
        // register hooks
        PlayerConnectionHooks.JOIN.registerWith(hooks, this::onJoin);
        PlayerConnectionHooks.QUIT.registerWith(hooks, this::onQuit);
        LanguageChangedCallback.HOOK.registerWith(hooks, this::onLanguageChanged);

        // sync state with currently online players
        synchronized (this) {
            for (ServerPlayer player : allPlayers) {
                String lang = languageGetter.apply(player);

                String oldLang = playerLanguage.put(player.getUUID(), lang);

                if (oldLang != null) {
                    if (oldLang.equals(lang)) continue;

                    decrementUserCount(lang);
                }

                incrementUserCount(lang);
            }
        }

        // load all initially used languages, off-thread as it is blocking
        dispatch(() -> {
            boolean needsUpdate = false;

            synchronized (this) {
                for (String lang : languageUserCount.keySet()) {
                    if (languageUserCount.getInt(lang) <= 0) continue;

                    translations.addLanguage(lang);
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                updateCallback.run();
            }
        });
    }

    private void onJoin(ServerPlayer player) {
        String lang = languageGetter.apply(player);
        changeLanguage(player, lang);
    }

    private void onQuit(ServerPlayer player) {
        String lang = languageGetter.apply(player);

        synchronized (this) {
            playerLanguage.remove(player.getUUID());

            int newUserCount = decrementUserCount(lang);
            unloadLanguageIfNoUsers(lang, newUserCount);
        }
    }

    private void onLanguageChanged(ServerPlayer player, String language, LanguageChangedCallback.Reason reason) {
        changeLanguage(player, language);
    }

    private synchronized void changeLanguage(ServerPlayer player, String language) {
        String oldLang = playerLanguage.put(player.getUUID(), language);

        if (oldLang != null) {
            if (oldLang.equals(language)) return;

            int newUserCount = decrementUserCount(oldLang);
            unloadLanguageIfNoUsers(oldLang, newUserCount);
        }

        incrementUserCount(language);
        loadLanguageIfNew(language);
    }

    private void incrementUserCount(String lang) {
        languageUserCount.computeInt(lang, (key, count) -> count == null ? 1 : count + 1);
    }

    private int decrementUserCount(String lang) {
        return languageUserCount.computeInt(lang, (key, count) -> count == null || count <= 1 ? null : count - 1);
    }

    private void loadLanguageIfNew(String lang) {
        if (translations.hasLanguage(lang)) return;

        // add language off-thread, as it is blocking
        dispatch(() -> {
            if (translations.addLanguage(lang)) {
                updateCallback.run();
            }
        });
    }

    private void unloadLanguageIfNoUsers(String lang, int userCount) {
        if (userCount > 0) return;

        // remove language off-thread, as it is blocking
        dispatch(() -> {
            if (translations.removeLanguage(lang)) {
                updateCallback.run();
            }
        });
    }

    @VisibleForTesting
    void dispatch(Runnable action) {
        Thread.startVirtualThread(action);
    }
}
