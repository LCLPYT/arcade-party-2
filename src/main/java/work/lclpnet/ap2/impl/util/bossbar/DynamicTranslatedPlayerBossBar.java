package work.lclpnet.ap2.impl.util.bossbar;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class DynamicTranslatedPlayerBossBar implements PlayerBossBar {

    private final Identifier id;
    private final TranslationService translations;
    private final BossBarProvider bossBarProvider;
    private final Map<UUID, Entry> entries = new WeakHashMap<>();
    private final String translationKey;
    private final Object[] arguments;
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Style style = BossBar.Style.PROGRESS;
    private float percent = 0f;
    private boolean visible = true;
    private Style titleStyle = Style.EMPTY;

    public DynamicTranslatedPlayerBossBar(Identifier id, String translationKey, Object[] arguments,
                                          TranslationService translations, BossBarProvider bossBarProvider) {
        this.id = id;
        this.translationKey = translationKey;
        this.arguments = arguments;
        this.translations = translations;
        this.bossBarProvider = bossBarProvider;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerConnectionHooks.QUIT, this::remove);
        hooks.registerHook(LanguageChangedCallback.HOOK, (player, language, reason) -> update(player));
    }

    private ServerBossBar createBossBar(ServerPlayerEntity player) {
        Identifier suffixedId = id.withSuffixedPath("/" + player.getEntityName().toLowerCase(Locale.ROOT));
        RootText title = translations.translateText(player, translationKey, arguments).setStyle(titleStyle);

        CommandBossBar bossBar = bossBarProvider.createBossBar(suffixedId, title);
        bossBar.setColor(color);
        bossBar.setStyle(style);
        bossBar.setPercent(percent);
        bossBar.setVisible(visible);

        return bossBar;
    }

    private Entry createEntry(ServerPlayerEntity player) {
        return new Entry(createBossBar(player), translationKey, arguments);
    }

    @Override
    public ServerBossBar getBossBar(ServerPlayerEntity player) {
        return getOrCreateEntry(player).bossBar;
    }

    @NotNull
    private Entry getOrCreateEntry(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), uuid -> createEntry(player));
    }

    @Nullable
    private Entry getEntry(ServerPlayerEntity player) {
        return entries.get(player.getUuid());
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        Entry entry = entries.remove(player.getUuid());
        if (entry == null) return;

        entry.bossBar.removePlayer(player);

        // Note: boss bar unregistering is not handled by this class
    }

    public void setTranslationKey(ServerPlayerEntity player, String translationKey) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.translationKey = translationKey;
        update(player, entry);
    }

    public void setArguments(ServerPlayerEntity player, Object[] arguments) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.arguments = arguments;
        update(player, entry);
    }

    public void setArgument(ServerPlayerEntity player, int i, Object argument) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.arguments[i] = argument;
        update(player, entry);
    }

    private void update(ServerPlayerEntity player) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        update(player, entry);
    }

    private void update(ServerPlayerEntity player, Entry entry) {
        var title = translations.translateText(player, entry.translationKey, entry.arguments)
                .setStyle(titleStyle);

        entry.bossBar.setName(title);
    }

    private void updateBars(Consumer<ServerBossBar> action) {
        for (Entry entry : entries.values()) {
            action.accept(entry.bossBar);
        }
    }

    public void setPercent(float percent) {
        this.percent = percent;

        updateBars(bar -> bar.setPercent(this.percent));
    }

    public void setColor(BossBar.Color color) {
        this.color = color;

        updateBars(bar -> bar.setColor(this.color));
    }

    public void setStyle(BossBar.Style style) {
        this.style = style;

        updateBars(bar -> bar.setStyle(this.style));
    }

    public void setVisible(boolean visible) {
        this.visible = visible;

        updateBars(bar -> bar.setVisible(this.visible));
    }

    public Style getTitleStyle() {
        return titleStyle;
    }

    public void setTitleStyle(Style style) {
        this.titleStyle = style;
    }

    /**
     * Updates the style of the title text.
     *
     * @see #getTitleStyle()
     * @see #setTitleStyle(net.minecraft.text.Style)
     *
     * @param styleUpdater the style updater
     */
    public DynamicTranslatedPlayerBossBar styled(UnaryOperator<Style> styleUpdater) {
        this.setTitleStyle(styleUpdater.apply(this.getTitleStyle()));
        return this;
    }

    /**
     * Fills the absent parts of the title text's style with definitions from {@code styleOverride}.
     *
     * @see net.minecraft.text.Style#withParent(net.minecraft.text.Style)
     *
     * @param styleOverride the style that provides definitions for absent definitions in the title text's style
     */
    public DynamicTranslatedPlayerBossBar fillStyle(Style styleOverride) {
        this.setTitleStyle(styleOverride.withParent(this.getTitleStyle()));
        return this;
    }

    /**
     * Adds some formattings to the title text's style.
     *
     * @param formattings an array of formattings
     */
    public DynamicTranslatedPlayerBossBar formatted(Formatting... formattings) {
        this.setTitleStyle(this.getTitleStyle().withFormatting(formattings));
        return this;
    }

    /**
     * Add a formatting to the title text's style.
     *
     * @param formatting a formatting
     */
    public DynamicTranslatedPlayerBossBar formatted(Formatting formatting) {
        this.setTitleStyle(this.getTitleStyle().withFormatting(formatting));
        return this;
    }

    private static class Entry {
        final ServerBossBar bossBar;
        String translationKey;
        Object[] arguments;

        private Entry(ServerBossBar bossBar, String translationKey, Object[] arguments) {
            this.bossBar = Objects.requireNonNull(bossBar);
            this.translationKey = translationKey;
            this.arguments = arguments;
        }
    }
}
