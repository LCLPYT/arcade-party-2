package work.lclpnet.ap2.impl.util.bossbar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class DynamicTranslatedPlayerBossBar implements PlayerBossBar {

    private final Identifier id;
    private final Translations translations;
    private final BossBarProvider bossBarProvider;
    private final Map<UUID, Entry> entries = new WeakHashMap<>();
    private final String translationKey;
    private final Object[] arguments;
    private BossEvent.BossBarColor color = BossEvent.BossBarColor.WHITE;
    private BossEvent.BossBarOverlay style = BossEvent.BossBarOverlay.PROGRESS;
    private float percent = 0f;
    private boolean visible = true;
    private Style titleStyle = Style.EMPTY;

    public DynamicTranslatedPlayerBossBar(Identifier id, String translationKey, Object[] arguments,
                                          Translations translations, BossBarProvider bossBarProvider) {
        this.id = id;
        this.translationKey = translationKey;
        this.arguments = arguments;
        this.translations = translations;
        this.bossBarProvider = bossBarProvider;
    }

    public void init(HookRegistrar hooks) {
        PlayerConnectionHooks.QUIT.registerWith(hooks, this::remove);
        LanguageChangedCallback.HOOK.registerWith(hooks, (player, _, _) -> update(player));
    }

    private ServerBossEvent createBossBar(ServerPlayer player) {
        Identifier suffixedId = id.withSuffix("/" + player.getScoreboardName().toLowerCase(Locale.ROOT));
        RootText title = translations.translateText(player, translationKey, arguments).setStyle(titleStyle);

        CustomBossEvent bossBar = bossBarProvider.createBossBar(suffixedId, title);
        bossBar.setColor(color);
        bossBar.setOverlay(style);
        bossBar.setProgress(percent);
        bossBar.setVisible(visible);

        return bossBar;
    }

    private Entry createEntry(ServerPlayer player) {
        return new Entry(createBossBar(player), translationKey, arguments);
    }

    @Override
    public ServerBossEvent getBossBar(ServerPlayer player) {
        return getOrCreateEntry(player).bossBar;
    }

    @NotNull
    private Entry getOrCreateEntry(ServerPlayer player) {
        return entries.computeIfAbsent(player.getUUID(), _ -> createEntry(player));
    }

    @Nullable
    private Entry getEntry(ServerPlayer player) {
        return entries.get(player.getUUID());
    }

    @Override
    public void remove(ServerPlayer player) {
        Entry entry = entries.remove(player.getUUID());
        if (entry == null) return;

        entry.bossBar.removePlayer(player);

        // Note: boss bar unregistering is not handled by this class
    }

    public void setTranslationKey(ServerPlayer player, String translationKey) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.translationKey = translationKey;
        update(player, entry);
    }

    public void setArguments(ServerPlayer player, Object[] arguments) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.arguments = arguments;
        update(player, entry);
    }

    public void setArgument(ServerPlayer player, int i, Object argument) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        entry.arguments[i] = argument;
        update(player, entry);
    }

    private void update(ServerPlayer player) {
        Entry entry = getEntry(player);
        if (entry == null) return;

        update(player, entry);
    }

    private void update(ServerPlayer player, Entry entry) {
        var title = translations.translateText(player, entry.translationKey, entry.arguments)
                .setStyle(titleStyle);

        entry.bossBar.setName(title);
    }

    private void updateBars(Consumer<ServerBossEvent> action) {
        for (Entry entry : entries.values()) {
            action.accept(entry.bossBar);
        }
    }

    public void setPercent(float percent) {
        this.percent = percent;

        updateBars(bar -> bar.setProgress(this.percent));
    }

    public void setColor(BossEvent.BossBarColor color) {
        this.color = color;

        updateBars(bar -> bar.setColor(this.color));
    }

    public void setStyle(BossEvent.BossBarOverlay style) {
        this.style = style;

        updateBars(bar -> bar.setOverlay(this.style));
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
     * @see #setTitleStyle(net.minecraft.network.chat.Style)
     *
     * @param styleUpdater the style updater
     */
    public DynamicTranslatedPlayerBossBar withStyle(UnaryOperator<Style> styleUpdater) {
        this.setTitleStyle(styleUpdater.apply(this.getTitleStyle()));
        return this;
    }

    /**
     * Fills the absent parts of the title text's style with definitions from {@code styleOverride}.
     *
     * @see net.minecraft.network.chat.Style#applyTo(net.minecraft.network.chat.Style)
     *
     * @param styleOverride the style that provides definitions for absent definitions in the title text's style
     */
    public DynamicTranslatedPlayerBossBar withStyle(Style styleOverride) {
        this.setTitleStyle(styleOverride.applyTo(this.getTitleStyle()));
        return this;
    }

    /**
     * Adds some formattings to the title text's style.
     *
     * @param formattings an array of formattings
     */
    public DynamicTranslatedPlayerBossBar withStyle(ChatFormatting... formattings) {
        this.setTitleStyle(this.getTitleStyle().applyFormats(formattings));
        return this;
    }

    /**
     * Add a formatting to the title text's style.
     *
     * @param formatting a formatting
     */
    public DynamicTranslatedPlayerBossBar withStyle(ChatFormatting formatting) {
        this.setTitleStyle(this.getTitleStyle().applyFormat(formatting));
        return this;
    }

    /**
     * Set the color of the title text's style.
     *
     * @param color The packed color int.
     */
    public DynamicTranslatedPlayerBossBar withColor(int color) {
        this.setTitleStyle(this.getTitleStyle().withColor(color));
        return this;
    }

    /**
     * Set the color of the title text's style.
     *
     * @param color The text color.
     */
    public DynamicTranslatedPlayerBossBar withColor(TextColor color) {
        this.setTitleStyle(this.getTitleStyle().withColor(color));
        return this;
    }

    private static class Entry {
        final ServerBossEvent bossBar;
        String translationKey;
        Object[] arguments;

        private Entry(ServerBossEvent bossBar, String translationKey, Object[] arguments) {
            this.bossBar = Objects.requireNonNull(bossBar);
            this.translationKey = translationKey;
            this.arguments = arguments;
        }
    }
}
