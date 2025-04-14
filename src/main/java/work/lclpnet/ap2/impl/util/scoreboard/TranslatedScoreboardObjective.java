package work.lclpnet.ap2.impl.util.scoreboard;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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

public class TranslatedScoreboardObjective implements
        CustomScoreboardObjective,
        StyleTransformer<TranslatedScoreboardObjective>,
        InformativeScoreboard,
        VirtualScoreboardObjective {

    private final Translations translations;
    private final PlayerManager playerManager;
    private final String name;
    private final ScoreboardCriterion.RenderType renderType;
    private final Map<CustomObjective, Set<UUID>> objectivePlayers = new HashMap<>();
    private final Map<String, CustomObjective> localizedObjectives = new HashMap<>();
    private final Map<UUID, String> players = new HashMap<>();
    private final Object2IntMap<String> scores = new Object2IntOpenHashMap<>();
    private final Map<String, CustomEntry> entries = new HashMap<>();
    private final ScoreboardLayout layout = new ScoreboardLayout();
    private CustomEntry defaultEntry = new CustomEntry(null, null, StyledNumberFormat.RED);
    private String translationKey;
    private Object[] args;
    private ScoreboardDisplaySlot slot = null;
    @Setter @Getter
    private Style style = Style.EMPTY;
    @Nullable
    private Function<String, @Nullable Text> displayFunction = null;

    public TranslatedScoreboardObjective(Translations translations, PlayerManager playerManager, String name,
                                         ScoreboardCriterion.RenderType renderType, String translationKey, Object[] args) {
        this.translations = translations;
        this.playerManager = playerManager;
        this.name = name;
        this.renderType = renderType;
        this.translationKey = translationKey;
        this.args = args;
    }

    @Override
    public void add(ServerPlayerEntity player) {
        final String language = translations.getLanguage(player);
        final UUID uuid = player.getUuid();

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
    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
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
    public void update(ServerPlayerEntity player) {
        if (!players.containsKey(player.getUuid())) return;

        // adding the player will update the language
        add(player);
    }

    @NotNull
    private CustomObjective getLocalizedObjective(String language) {
        return localizedObjectives.computeIfAbsent(language, this::createLocalizedObjective);
    }

    @NotNull
    private CustomObjective createLocalizedObjective(String language) {
        Text localizedTitle = getLocalizedTitle(language);

        String suffix = language.replaceAll("[^a-zA-Z0-9._-]", "");  // remove invalid characters
        String localizedName = name + "_" + (suffix);

        return new CustomObjective(localizedName, localizedTitle, renderType, defaultEntry.numberFormat());
    }

    @NotNull
    private Text getLocalizedTitle(String language) {
        RootText rootText = translations.translateText(language, translationKey, args);
        rootText.setStyle(style);

        return rootText;
    }

    public void setTitle(String translationKey, Object... args) {
        this.translationKey = translationKey;
        this.args = args;

        for (var entry : localizedObjectives.entrySet()) {
            Text localizedTitle = getLocalizedTitle(entry.getKey());

            CustomObjective objective = entry.getValue();
            objective.setTitle(localizedTitle);

            Set<UUID> uuids = objectivePlayers.get(objective);

            for (UUID uuid : uuids) {
                ServerPlayerEntity player = playerManager.getPlayer(uuid);
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

    public void setSlot(@Nullable ScoreboardDisplaySlot slot) {
        if (slot == this.slot) return;

        ScoreboardDisplaySlot prevSlot = this.slot;
        this.slot = slot;

        for (var entry : players.entrySet()) {
            ServerPlayerEntity player = playerManager.getPlayer(entry.getKey());

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
    public void setDisplayName(String scoreHolder, @Nullable Text display) {
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

    public void setDisplayName(@Nullable Function<String, @Nullable Text> displayFunction) {
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
            Text display = displayFunction.apply(scoreHolder);
            return defaultEntry.withDisplay(display);
        });
    }

    private void syncEntry(String scoreHolder) {
        int score = getScore(scoreHolder);
        updateObjectives(objective -> syncScore(objective, scoreHolder, score));
    }

    private void syncScores(CustomObjective objective, ServerPlayerEntity player) {
        scores.forEach((scoreHolder, score) -> {
            var entry = getEntry(scoreHolder);
            Text display = getScoreHolderDisplay(entry, player);
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
            ServerPlayerEntity player = playerManager.getPlayer(uuid);
            if (player == null) continue;

            Text display = getScoreHolderDisplay(entry, player);

            objective.sendScore(player, scoreHolder, score, display, format);
        }
    }

    @Nullable
    private Text getScoreHolderDisplay(CustomEntry entry, ServerPlayerEntity viewer) {
        Text display = entry.display();
        TextTranslatable translatedDisplay = entry.translatedDisplay();

        if (translatedDisplay == null) {
            return display;
        }

        String language = translations.getLanguage(viewer);

        return translatedDisplay.translateTo(language);
    }

    @Override
    public ScoreHandle createText(Text text, int position) {
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

    private @NotNull ScoreHandle createHandle(int position) {
        String holder = UUID.randomUUID().toString();
        ScoreHandle handle = new ScoreHandle(holder, this);

        setScore(holder, layout.resolvePosition(position));

        handle.setNumberFormat(BlankNumberFormat.INSTANCE);
        return handle;
    }

    @Override
    public void unload() {
        objectivePlayers.forEach((objective, uuids) -> {
            for (UUID uuid : uuids) {
                ServerPlayerEntity player = playerManager.getPlayer(uuid);
                if (player == null) continue;

                objective.remove(player);
            }
        });
    }

    public record CustomEntry(@Nullable Text display, @Nullable TextTranslatable translatedDisplay,
                              NumberFormat numberFormat) {

        public CustomEntry withDisplay(@Nullable Text display) {
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
