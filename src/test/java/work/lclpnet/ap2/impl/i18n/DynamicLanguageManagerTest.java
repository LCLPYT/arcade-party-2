package work.lclpnet.ap2.impl.i18n;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.hook.HookContainer;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DynamicLanguageManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamicLanguageManagerTest.class);
    private Map<ServerPlayerEntity, String> playerLanguage;
    private DynamicLanguageManager languageManager;
    private int updateCount;
    private HookContainer hooks;
    private VanillaTranslations translations;

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @BeforeEach
    void setUp() {
        playerLanguage = new HashMap<>();
        updateCount = 0;

        translations = spy(new VanillaTranslations(mock(), logger));

        // simulate add/remove/has language
        Set<String> addedLanguages = new HashSet<>();

        doAnswer(invocation -> addedLanguages.add(invocation.getArgument(0, String.class)))
                .when(translations).addLanguage(anyString());

        doAnswer(invocation -> addedLanguages.remove(invocation.getArgument(0, String.class)))
                .when(translations).removeLanguage(anyString());

        doAnswer(invocation -> {
            String lang = invocation.getArgument(0, String.class);
            return "en_us".equals(lang) || addedLanguages.contains(lang);
        }).when(translations).hasLanguage(anyString());

        languageManager = spy(new DynamicLanguageManager(translations,
                (player) -> playerLanguage.getOrDefault(player, "en_us"),
                () -> updateCount++));

        doAnswer(invocation -> {
            var action = invocation.getArgument(0, Runnable.class);

            // this would normally run off-thread, but it should be in the same thread for tests
            action.run();

            return null;
        }).when(languageManager).dispatch(any());

        hooks = new HookContainer();
    }

    @AfterEach
    void tearDown() {
        hooks.unload();
    }

    @Test
    void simulateJoin_english() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity player = player();
        playerLanguage.put(player, "en_us");

        PlayerConnectionHooks.JOIN.invoker().act(player);

        assertEquals(Map.of("en_us", 1), languageManager.languageUserCount);

        // en_us is the default language, it cannot be added dynamically, therefore there shouldn't have been an update
        verify(translations, times(1)).hasLanguage("en_us");
        verify(translations, never()).addLanguage(anyString());

        assertEquals(0, updateCount);
    }

    @Test
    void simulateJoin_german() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity player = player();
        playerLanguage.put(player, "de_de");

        PlayerConnectionHooks.JOIN.invoker().act(player);

        assertEquals(Map.of("de_de", 1), languageManager.languageUserCount);

        // de_de should be loaded and therefore there should be one update
        assertEquals(1, updateCount);
    }

    @Test
    void simulateJoin_german_twice() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity playerOne = player(), playerTwo = player();
        playerLanguage.put(playerOne, "de_de");
        playerLanguage.put(playerTwo, "de_de");

        PlayerConnectionHooks.JOIN.invoker().act(playerOne);
        PlayerConnectionHooks.JOIN.invoker().act(playerTwo);

        assertEquals(Map.of("de_de", 2), languageManager.languageUserCount);

        // should still only be one update
        assertEquals(1, updateCount);
    }

    @Test
    void simulateQuit_english() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity player = player();
        playerLanguage.put(player, "en_us");

        PlayerConnectionHooks.JOIN.invoker().act(player);
        PlayerConnectionHooks.QUIT.invoker().act(player);

        assertEquals(Map.of(), languageManager.languageUserCount);

        // en_us is the default language and cannot be unloaded, thereby it causes no update
        assertEquals(0, updateCount);
    }

    @Test
    void simulateQuit_german() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity player = player();
        playerLanguage.put(player, "de_de");

        PlayerConnectionHooks.JOIN.invoker().act(player);
        PlayerConnectionHooks.QUIT.invoker().act(player);

        assertEquals(Map.of(), languageManager.languageUserCount);

        // de_de should be loaded once and then unloaded, therefore there should have been two updates
        assertEquals(2, updateCount);
    }

    @Test
    void simulateChangeLanguage() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity player = player();
        playerLanguage.put(player, "de_de");

        PlayerConnectionHooks.JOIN.invoker().act(player);
        LanguageChangedCallback.HOOK.invoker().onChanged(player, "en_us", LanguageChangedCallback.Reason.PLAYER);

        assertEquals(Map.of("en_us", 1), languageManager.languageUserCount);

        // de_de should be loaded once and then unloaded
        assertEquals(2, updateCount);
    }

    @Test
    void simulateChangeLanguage_withOtherPlayers() {
        languageManager.init(hooks, List.of());

        ServerPlayerEntity playerOne = player(), playerTwo = player();
        playerLanguage.put(playerOne, "de_de");
        playerLanguage.put(playerTwo, "de_de");

        PlayerConnectionHooks.JOIN.invoker().act(playerOne);
        PlayerConnectionHooks.JOIN.invoker().act(playerTwo);
        LanguageChangedCallback.HOOK.invoker().onChanged(playerOne, "en_us", LanguageChangedCallback.Reason.PLAYER);

        assertEquals(Map.of("en_us", 1, "de_de", 1), languageManager.languageUserCount);

        // de_de should be loaded once but not unloaded, because there is another player that uses it
        assertEquals(1, updateCount);
    }

    @Test
    void init_withPlayers_updateBatched() {
        ServerPlayerEntity playerOne = player(), playerTwo = player();
        playerLanguage.put(playerOne, "de_de");
        playerLanguage.put(playerTwo, "ja_jp");

        languageManager.init(hooks, List.of(playerOne, playerTwo));

        assertEquals(Map.of("de_de", 1, "ja_jp", 1), languageManager.languageUserCount);

        // both languages should have been loaded at once
        assertEquals(1, updateCount);
    }

    ServerPlayerEntity player() {
        ServerPlayerEntity player = mock();
        UUID uuid = UUID.randomUUID();

        when(player.getUuid()).thenReturn(uuid);

        return player;
    }
}