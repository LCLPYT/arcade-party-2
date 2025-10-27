package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Setter;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.api.util.scoreboard.InformativeScoreboard;
import work.lclpnet.ap2.api.util.scoreboard.VirtualScoreboardObjective;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One vanilla objective for each player.
 */
public class DynamicScoreboardObjective implements
        CustomScoreboardObjective,
        InformativeScoreboard,
        VirtualScoreboardObjective {

    private final String name;
    private final ScoreboardCriterion.RenderType renderType;
    private final Function<ServerPlayerEntity, Text> title;
    private final PlayerManager playerManager;
    private final Map<UUID, CustomObjective> objectives = new HashMap<>();
    private final ScoreboardLayout layout = new ScoreboardLayout();
    private final Map<String, DynamicEntry> entries = new HashMap<>();

    private ScoreboardDisplaySlot slot = null;
    @Setter
    private NumberFormat defaultNumberFormat = StyledNumberFormat.RED;
    @Setter
    private BiFunction<ServerPlayerEntity, String, Text> defaultDisplay = (player, holder) -> Text.literal(holder);

    public DynamicScoreboardObjective(String name, ScoreboardCriterion.RenderType renderType,
                                      Function<ServerPlayerEntity, Text> title, PlayerManager playerManager) {
        this.name = name;
        this.renderType = renderType;
        this.title = title;
        this.playerManager = playerManager;
    }

    @Override
    public void add(ServerPlayerEntity player) {
        CustomObjective objective = getOrCreateObjective(player);

        entries.values().forEach(dynamicEntry -> dynamicEntry.put(player, objective));

        objective.add(player);
        objective.setDisplay(player, slot);
        objective.syncScores(player);
    }

    private @NotNull CustomObjective getOrCreateObjective(ServerPlayerEntity player) {
        return objectives.computeIfAbsent(player.getUuid(), uuid -> createObjective(player));
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        CustomObjective objective = objectives.remove(player.getUuid());

        if (objective == null) return;

        CustomObjective.setDisplay(player, null, slot);
        objective.remove(player);
    }

    @Override
    public void update(ServerPlayerEntity player) {
        if (!objectives.containsKey(player.getUuid())) return;

        remove(player);
        add(player);
    }

    protected @NotNull CustomObjective createObjective(ServerPlayerEntity player) {
        String objectiveName = name + "_" + player.getNameForScoreboard();
        Text display = title.apply(player);

        return new CustomObjective(objectiveName, display, renderType, StyledNumberFormat.RED);
    }

    public void setSlot(@Nullable ScoreboardDisplaySlot slot) {
        if (slot == this.slot) return;

        ScoreboardDisplaySlot prevSlot = this.slot;
        this.slot = slot;

        eachObjective((player, objective) -> {
            if (prevSlot != null) {
                CustomObjective.setDisplay(player, null, prevSlot);
            }

            objective.setDisplay(player, slot);
        });
    }

    @Override
    public void setScore(String scoreHolder, int score) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);
        entry.defaultScore = score;

        entry.eachPlayerEntry(playerEntry -> playerEntry.score = score);

        modifyEntry(scoreHolder, e -> e.setScore(score));
    }

    public void setScore(ServerPlayerEntity player, String scoreHolder, int score) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);

        entry.getOrCreatePlayerEntry(player).score = score;

        modifyEntry(player, scoreHolder, e -> e.setScore(score));
    }

    @Override
    public void setDisplayName(String scoreHolder, @Nullable Text display) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);
        entry.displayName = player -> display;

        entry.eachPlayerEntry(playerEntry -> playerEntry.display = display);

        modifyEntry(scoreHolder, e -> e.setDisplay(display));
    }

    public void setDisplayName(ServerPlayerEntity player, String scoreHolder, @Nullable Text display) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);

        entry.getOrCreatePlayerEntry(player).display = display;

        modifyEntry(player, scoreHolder, e -> e.setDisplay(display));
    }

    @Override
    public void setNumberFormat(String scoreHolder, NumberFormat numberFormat) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);
        entry.defaultNumberFormat = numberFormat;

        entry.eachPlayerEntry(playerEntry -> playerEntry.numberFormat = numberFormat);

        modifyEntry(scoreHolder, e -> e.setNumberFormat(numberFormat));
    }

    public void setNumberFormat(ServerPlayerEntity player, String scoreHolder, NumberFormat numberFormat) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);

        entry.getOrCreatePlayerEntry(player).numberFormat = numberFormat;

        modifyEntry(player, scoreHolder, e -> e.setNumberFormat(numberFormat));
    }

    @Override
    public void removeEntry(String scoreHolder) {
        entries.remove(scoreHolder);

        eachObjective((player, objective) -> {
            objective.remove(scoreHolder);
            objective.clear(player, scoreHolder);
        });
    }

    @NotNull
    private DynamicEntry getOrCreateEntry(String holder) {
        DynamicEntry entry = entries.getOrDefault(holder, null);

        if (entry != null) {
            return entry;
        }

        entry = new DynamicEntry(holder, 0, defaultNumberFormat, player -> defaultDisplay.apply(player, holder));

        setDynamicEntry(holder, entry);

        return entry;
    }

    private void setDynamicEntry(String holder, DynamicEntry entry) {
        entries.put(holder, entry);

        eachObjective(entry::put);
    }

    @Override
    public ScoreHandle createText(Text line, int position) {
        return createText(p -> line, position);
    }

    @Override
    public ScoreHandle createText(TranslatedText line, int position) {
        return createText(line::translateFor, position);
    }

    public ScoreHandle createText(Function<ServerPlayerEntity, @Nullable Text> textFactory, int position) {
        String holder = UUID.randomUUID().toString();
        int score = layout.resolvePosition(position);

        var entry = new DynamicEntry(holder, score, BlankNumberFormat.INSTANCE, textFactory);

        setDynamicEntry(holder, entry);

        return new ScoreHandle(holder, this);
    }

    public DynamicScoreHandle createDynamicText(TranslatedText line, int position) {
        return createDynamicText(line::translateFor, position);
    }

    public DynamicScoreHandle createDynamicText(Function<ServerPlayerEntity, @Nullable Text> textFactory, int position) {
        String holder = UUID.randomUUID().toString();
        int score = layout.resolvePosition(position);

        var entry = new DynamicEntry(holder, score, BlankNumberFormat.INSTANCE, textFactory);

        setDynamicEntry(holder, entry);

        return new DynamicScoreHandle(holder, this);
    }

    protected void modifyEntry(String scoreHolder, Consumer<CustomScoreboardEntry> action) {
        eachObjective(objective -> objective.getEntry(scoreHolder).ifPresent(action));
        eachObjective((player, objective) -> objective.syncScore(player, scoreHolder));
    }

    protected void eachObjective(BiConsumer<ServerPlayerEntity, CustomObjective> action) {
        objectives.forEach((uuid, objective) -> {
            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player != null) {
                action.accept(player, objective);
            }
        });
    }

    protected void eachObjective(Consumer<CustomObjective> action) {
        objectives.values().forEach(action);
    }

    protected void modifyEntry(ServerPlayerEntity player, String scoreHolder, Consumer<CustomScoreboardEntry> action) {
        CustomObjective objective = getOrCreateObjective(player);

        var customEntry = objective.getEntry(scoreHolder).orElseGet(() -> {
            DynamicEntry dynamicEntry = getOrCreateEntry(scoreHolder);
            CustomScoreboardEntry entry = dynamicEntry.getOrCreatePlayerEntry(player).createEntry();

            objective.setEntry(scoreHolder, entry);

            return entry;
        });

        action.accept(customEntry);

        objective.syncScore(player, scoreHolder);
    }

    @Override
    public void unload() {
        eachObjective((player, objective) -> {
            CustomObjective.setDisplay(player, null, slot);
            objective.remove(player);
        });

        objectives.clear();
    }

    private static final class DynamicEntry {
        private final String holder;
        private int defaultScore;
        private NumberFormat defaultNumberFormat;
        private Function<ServerPlayerEntity, @Nullable Text> displayName;
        private final Map<UUID, DynamicPlayerEntry> playerEntries = new HashMap<>();

        private DynamicEntry(String holder, int defaultScore, NumberFormat defaultNumberFormat, Function<ServerPlayerEntity, @Nullable Text> displayName) {
            this.holder = holder;
            this.defaultScore = defaultScore;
            this.defaultNumberFormat = defaultNumberFormat;
            this.displayName = displayName;
        }

        public void put(ServerPlayerEntity player, CustomObjective objective) {
            objective.setEntry(holder, createEntry(player));
        }

        public CustomScoreboardEntry createEntry(ServerPlayerEntity player) {
            return getOrCreatePlayerEntry(player).createEntry();
        }

        private @NotNull DynamicPlayerEntry getOrCreatePlayerEntry(ServerPlayerEntity player) {
            return playerEntries.computeIfAbsent(player.getUuid(), uuid -> {
                var wrapper = new DynamicPlayerEntry();
                wrapper.display = displayName.apply(player);
                wrapper.score = defaultScore;
                wrapper.numberFormat = defaultNumberFormat;

                return wrapper;
            });
        }

        public void eachPlayerEntry(Consumer<DynamicPlayerEntry> action) {
            for (DynamicPlayerEntry entry : playerEntries.values()) {
                action.accept(entry);
            }
        }
    }

    private static final class DynamicPlayerEntry {
        private int score;
        private NumberFormat numberFormat;
        private @Nullable Text display;

        public CustomScoreboardEntry createEntry() {
            return new CustomScoreboardEntry(display, numberFormat, score);
        }
    }
}
