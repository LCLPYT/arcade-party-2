package work.lclpnet.ap2.impl.util.scoreboard;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.StyleTransformer;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.api.util.scoreboard.InformativeScoreboard;
import work.lclpnet.ap2.api.util.scoreboard.VirtualScoreboardObjective;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One vanilla objective for each language.
 */
public class TranslatedScoreboardObjective implements
        CustomScoreboardObjective,
        StyleTransformer<TranslatedScoreboardObjective>,
        InformativeScoreboard,
        VirtualScoreboardObjective {

    private final Translations translations;
    private final PlayerList playerManager;
    private final String name;
    private final ObjectiveCriteria.RenderType renderType;
    private final Map<CustomObjective, Set<UUID>> objectivePlayers = new HashMap<>();
    private final Map<String, CustomObjective> localizedObjectives = new HashMap<>();
    private final Map<UUID, String> players = new HashMap<>();
    private final Object2IntMap<String> scores = new Object2IntOpenHashMap<>();
    private final Map<String, CustomEntry> entries = new HashMap<>();
    private final ScoreboardLayout layout = new ScoreboardLayout();
    private CustomEntry defaultEntry = new CustomEntry(null, null, StyledFormat.SIDEBAR_DEFAULT);
    private String translationKey;
    private Object[] args;
    private DisplaySlot slot = null;
    @Setter @Getter
    private Style style = Style.EMPTY;
    @Nullable
    private Function<String, @Nullable Component> displayFunction = null;

    public TranslatedScoreboardObjective(Translations translations, PlayerList playerManager, String name,
                                         ObjectiveCriteria.RenderType renderType, String translationKey, Object[] args) {
        this.translations = translations;
        this.playerManager = playerManager;
        this.name = name;
        this.renderType = renderType;
        this.translationKey = translationKey;
        this.args = args;
    }

    @Override
    public void add(ServerPlayer player) {
        final String language = translations.getLanguage(player);
        final UUID uuid = player.getUUID();

        final String oldLanguage = players.get(uuid);

        // check if language did change
        if (language.equals(oldLanguage)) return;

        if (oldLanguage != null) {
            // the language changed, remove the player from the old boss bar
            remove(player);
        }

        CustomObjective objective = getLocalizedObjective(language);

        objectivePlayers.computeIfAbsent(objective, ignored -> new HashSet<>()).add(uuid);

        objective.add(player);
        objective.setDisplay(player, slot);
        syncScores(objective, player);

        players.put(uuid, language);
    }

    @Override
    public void remove(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String lang = players.remove(uuid);
        if (lang == null) return;

        CustomObjective objective = localizedObjectives.get(lang);

        if (objective == null) return;

        CustomObjective.setDisplay(player, null, slot);
        objective.remove(player);

        Set<UUID> uuids = objectivePlayers.get(objective);
        uuids.remove(uuid);
    }

    @Override
    public void update(ServerPlayer player) {
        if (!players.containsKey(player.getUUID())) return;

        // adding the player will update the language
        add(player);
    }

    @NotNull
    private CustomObjective getLocalizedObjective(String language) {
        return localizedObjectives.computeIfAbsent(language, this::createLocalizedObjective);
    }

    @NotNull
    private CustomObjective createLocalizedObjective(String language) {
        Component localizedTitle = getLocalizedTitle(language);

        String suffix = language.replaceAll("[^a-zA-Z0-9._-]", "");  // remove invalid characters
        String localizedName = name + "_" + (suffix);

        return new CustomObjective(localizedName, localizedTitle, renderType, defaultEntry.numberFormat());
    }

    @NotNull
    private Component getLocalizedTitle(String language) {
        RootText rootText = translations.translateText(language, translationKey, args);
        rootText.setStyle(style);

        return rootText;
    }

    public void setTitle(String translationKey, Object... args) {
        this.translationKey = translationKey;
        this.args = args;

        for (var entry : localizedObjectives.entrySet()) {
            Component localizedTitle = getLocalizedTitle(entry.getKey());

            CustomObjective objective = entry.getValue();
            objective.setTitle(localizedTitle);

            Set<UUID> uuids = objectivePlayers.get(objective);

            for (UUID uuid : uuids) {
                ServerPlayer player = playerManager.getPlayer(uuid);
                if (player == null) continue;

                objective.update(player);
            }
        }
    }

    private void updateObjectives(Consumer<CustomObjective> action) {
        for (var objective : localizedObjectives.values()) {
            action.accept(objective);
        }
    }

    public void setSlot(@Nullable DisplaySlot slot) {
        if (slot == this.slot) return;

        DisplaySlot prevSlot = this.slot;
        this.slot = slot;

        for (var entry : players.entrySet()) {
            ServerPlayer player = playerManager.getPlayer(entry.getKey());

            if (player == null) continue;

            if (prevSlot != null) {
                CustomObjective.setDisplay(player, null, prevSlot);
            }

            if (slot == null) continue;  // hidden

            String lang = entry.getValue();
            CustomObjective objective = localizedObjectives.get(lang);

            if (objective == null) continue;

            CustomObjective.setDisplay(player, objective, slot);
        }
    }

    public int getScore(String scoreHolder) {
        return scores.getOrDefault(scoreHolder, 0);
    }

    @Override
    public void setScore(String scoreHolder, int score) {
        scores.put(scoreHolder, score);

        updateObjectives(objective -> syncScore(objective, scoreHolder, score));
    }

    @Override
    public void setDisplayName(String scoreHolder, @Nullable Component display) {
        CustomEntry entry = getEntry(scoreHolder);
        entries.put(scoreHolder, entry.withDisplay(display));
        syncEntry(scoreHolder);
    }

    public void setDisplayName(String scoreHolder, @Nullable TextTranslatable display) {
        CustomEntry entry = getEntry(scoreHolder);
        entries.put(scoreHolder, entry.withTranslatedDisplay(display));
        syncEntry(scoreHolder);
    }

    @Override
    public void setNumberFormat(String scoreHolder, NumberFormat numberFormat) {
        CustomEntry entry = getEntry(scoreHolder);
        entries.put(scoreHolder, entry.withNumberFormat(numberFormat));
        syncEntry(scoreHolder);
    }

    public void setDisplayName(@Nullable Function<String, @Nullable Component> displayFunction) {
        this.displayFunction = displayFunction;
    }

    public void setNumberFormat(NumberFormat numberFormat) {
        defaultEntry = defaultEntry.withNumberFormat(numberFormat);
    }

    private CustomEntry getEntry(String scoreHolder) {
        if (displayFunction == null) {
            return entries.getOrDefault(scoreHolder, defaultEntry);
        }

        return entries.computeIfAbsent(scoreHolder, s -> {
            Component display = displayFunction.apply(scoreHolder);
            return defaultEntry.withDisplay(display);
        });
    }

    private void syncEntry(String scoreHolder) {
        int score = getScore(scoreHolder);
        updateObjectives(objective -> syncScore(objective, scoreHolder, score));
    }

    private void syncScores(CustomObjective objective, ServerPlayer player) {
        scores.forEach((scoreHolder, score) -> {
            var entry = getEntry(scoreHolder);
            Component display = getScoreHolderDisplay(entry, player);
            NumberFormat format = entry.numberFormat();

            objective.sendScore(player, scoreHolder, score, display, format);
        });
    }

    private void syncScore(CustomObjective objective, String scoreHolder, int score) {
        Set<UUID> uuids = objectivePlayers.get(objective);
        if (uuids == null) return;

        var entry = getEntry(scoreHolder);
        NumberFormat format = entry.numberFormat();

        for (UUID uuid : uuids) {
            ServerPlayer player = playerManager.getPlayer(uuid);
            if (player == null) continue;

            Component display = getScoreHolderDisplay(entry, player);

            objective.sendScore(player, scoreHolder, score, display, format);
        }
    }

    @Nullable
    private Component getScoreHolderDisplay(CustomEntry entry, ServerPlayer viewer) {
        Component display = entry.display();
        TextTranslatable translatedDisplay = entry.translatedDisplay();

        if (translatedDisplay == null) {
            return display;
        }

        String language = translations.getLanguage(viewer);

        return translatedDisplay.translateTo(language);
    }

    @Override
    public ScoreHandle createText(Component text, int position) {
        ScoreHandle handle = createHandle(position);
        handle.setDisplay(text);

        return handle;
    }

    @Override
    public ScoreHandle createText(TranslatedText text, int position) {
        ScoreHandle handle = createHandle(position);

        setDisplayName(handle.getHolder(), text);

        return handle;
    }

    @Override
    public void removeEntry(String scoreHolder) {
        scores.removeInt(scoreHolder);
        entries.remove(scoreHolder);

        updateObjectives(objective -> {
            objective.remove(scoreHolder);

            Set<UUID> uuids = objectivePlayers.getOrDefault(objective, Set.of());

            for (UUID uuid : uuids) {
                ServerPlayer player = playerManager.getPlayer(uuid);

                if (player == null) continue;

                objective.clear(player, scoreHolder);
            }
        });
    }

    private @NotNull ScoreHandle createHandle(int position) {
        String holder = UUID.randomUUID().toString();
        ScoreHandle handle = new ScoreHandle(holder, this);

        setScore(holder, layout.resolvePosition(position));

        handle.setNumberFormat(BlankFormat.INSTANCE);
        return handle;
    }

    @Override
    public void unload() {
        objectivePlayers.forEach((objective, uuids) -> {
            for (UUID uuid : uuids) {
                ServerPlayer player = playerManager.getPlayer(uuid);
                if (player == null) continue;

                objective.remove(player);
            }
        });
    }

    public record CustomEntry(@Nullable Component display, @Nullable TextTranslatable translatedDisplay,
                              NumberFormat numberFormat) {

        public CustomEntry withDisplay(@Nullable Component display) {
            return new CustomEntry(display, null, this.numberFormat);
        }

        public CustomEntry withTranslatedDisplay(@Nullable TextTranslatable display) {
            return new CustomEntry(null, display, this.numberFormat);
        }

        public CustomEntry withNumberFormat(NumberFormat numberFormat) {
            return new CustomEntry(this.display, this.translatedDisplay, numberFormat);
        }
    }
}
