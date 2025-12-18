package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
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
    private final ObjectiveCriteria.RenderType renderType;
    private final Function<ServerPlayer, Component> title;
    private final PlayerList playerManager;
    private final Map<UUID, CustomObjective> objectives = new HashMap<>();
    private final ScoreboardLayout layout = new ScoreboardLayout();
    private final Map<String, DynamicEntry> entries = new HashMap<>();

    private DisplaySlot slot = null;
    @Setter
    private NumberFormat defaultNumberFormat = StyledFormat.SIDEBAR_DEFAULT;
    @Setter
    private BiFunction<ServerPlayer, String, Component> defaultDisplay = (player, holder) -> Component.literal(holder);

    public DynamicScoreboardObjective(String name, ObjectiveCriteria.RenderType renderType,
                                      Function<ServerPlayer, Component> title, PlayerList playerManager) {
        this.name = name;
        this.renderType = renderType;
        this.title = title;
        this.playerManager = playerManager;
    }

    @Override
    public void add(ServerPlayer player) {
        CustomObjective objective = getOrCreateObjective(player);

        entries.values().forEach(dynamicEntry -> dynamicEntry.put(player, objective));

        objective.add(player);
        objective.setDisplay(player, slot);
        objective.syncScores(player);
    }

    private @NotNull CustomObjective getOrCreateObjective(ServerPlayer player) {
        return objectives.computeIfAbsent(player.getUUID(), uuid -> createObjective(player));
    }

    @Override
    public void remove(ServerPlayer player) {
        CustomObjective objective = objectives.remove(player.getUUID());

        if (objective == null) return;

        CustomObjective.setDisplay(player, null, slot);
        objective.remove(player);
    }

    @Override
    public void update(ServerPlayer player) {
        if (!objectives.containsKey(player.getUUID())) return;

        remove(player);
        add(player);
    }

    protected @NotNull CustomObjective createObjective(ServerPlayer player) {
        String objectiveName = name + "_" + player.getScoreboardName();
        Component display = title.apply(player);

        return new CustomObjective(objectiveName, display, renderType, StyledFormat.SIDEBAR_DEFAULT);
    }

    public void setSlot(@Nullable DisplaySlot slot) {
        if (slot == this.slot) return;

        DisplaySlot prevSlot = this.slot;
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

    public void setScore(ServerPlayer player, String scoreHolder, int score) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);

        entry.getOrCreatePlayerEntry(player).score = score;

        modifyEntry(player, scoreHolder, e -> e.setScore(score));
    }

    @Override
    public void setDisplayName(String scoreHolder, @Nullable Component display) {
        DynamicEntry entry = getOrCreateEntry(scoreHolder);
        entry.displayName = player -> display;

        entry.eachPlayerEntry(playerEntry -> playerEntry.display = display);

        modifyEntry(scoreHolder, e -> e.setDisplay(display));
    }

    public void setDisplayName(ServerPlayer player, String scoreHolder, @Nullable Component display) {
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

    public void setNumberFormat(ServerPlayer player, String scoreHolder, NumberFormat numberFormat) {
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
    public ScoreHandle createText(Component line, int position) {
        return createText(p -> line, position);
    }

    @Override
    public ScoreHandle createText(TranslatedText line, int position) {
        return createText(line::translateFor, position);
    }

    public ScoreHandle createText(Function<ServerPlayer, @Nullable Component> textFactory, int position) {
        String holder = UUID.randomUUID().toString();
        int score = layout.resolvePosition(position);

        var entry = new DynamicEntry(holder, score, BlankFormat.INSTANCE, textFactory);

        setDynamicEntry(holder, entry);

        return new ScoreHandle(holder, this);
    }

    public DynamicScoreHandle createDynamicText(TranslatedText line, int position) {
        return createDynamicText(line::translateFor, position);
    }

    public DynamicScoreHandle createDynamicText(Function<ServerPlayer, @Nullable Component> textFactory, int position) {
        String holder = UUID.randomUUID().toString();
        int score = layout.resolvePosition(position);

        var entry = new DynamicEntry(holder, score, BlankFormat.INSTANCE, textFactory);

        setDynamicEntry(holder, entry);

        return new DynamicScoreHandle(holder, this);
    }

    protected void modifyEntry(String scoreHolder, Consumer<CustomScoreboardEntry> action) {
        eachObjective(objective -> objective.getEntry(scoreHolder).ifPresent(action));
        eachObjective((player, objective) -> objective.syncScore(player, scoreHolder));
    }

    protected void eachObjective(BiConsumer<ServerPlayer, CustomObjective> action) {
        objectives.forEach((uuid, objective) -> {
            ServerPlayer player = playerManager.getPlayer(uuid);

            if (player != null) {
                action.accept(player, objective);
            }
        });
    }

    protected void eachObjective(Consumer<CustomObjective> action) {
        objectives.values().forEach(action);
    }

    protected void modifyEntry(ServerPlayer player, String scoreHolder, Consumer<CustomScoreboardEntry> action) {
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
        private Function<ServerPlayer, @Nullable Component> displayName;
        private final Map<UUID, DynamicPlayerEntry> playerEntries = new HashMap<>();

        private DynamicEntry(String holder, int defaultScore, NumberFormat defaultNumberFormat, Function<ServerPlayer, @Nullable Component> displayName) {
            this.holder = holder;
            this.defaultScore = defaultScore;
            this.defaultNumberFormat = defaultNumberFormat;
            this.displayName = displayName;
        }

        public void put(ServerPlayer player, CustomObjective objective) {
            objective.setEntry(holder, createEntry(player));
        }

        public CustomScoreboardEntry createEntry(ServerPlayer player) {
            return getOrCreatePlayerEntry(player).createEntry();
        }

        private @NotNull DynamicPlayerEntry getOrCreatePlayerEntry(ServerPlayer player) {
            return playerEntries.computeIfAbsent(player.getUUID(), uuid -> {
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
        private @Nullable Component display;

        public CustomScoreboardEntry createEntry() {
            return new CustomScoreboardEntry(display, numberFormat, score);
        }
    }
}
