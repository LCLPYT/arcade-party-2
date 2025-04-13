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
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class DynamicScoreboardObjective implements CustomScoreboardObjective, InformativeScoreboard {

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

    public void addPlayer(ServerPlayerEntity player) {
        CustomObjective objective = objectives.computeIfAbsent(player.getUuid(), uuid -> getObjective(player));

        entries.values().forEach(dynamicEntry -> dynamicEntry.put(player, objective));

        objective.add(player);
        objective.setDisplay(player, slot);
        objective.syncScores(player);
    }

    public void removePlayer(ServerPlayerEntity player) {
        CustomObjective objective = objectives.remove(player.getUuid());

        if (objective == null) return;

        CustomObjective.setDisplay(player, null, slot);
        objective.remove(player);
    }

    protected @NotNull CustomObjective getObjective(ServerPlayerEntity player) {
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
        getOrCreateEntry(scoreHolder).score = score;

        modifyEntry(scoreHolder, e -> e.setScore(score));
    }

    @Override
    public void setDisplayName(String scoreHolder, @Nullable Text display) {
        getOrCreateEntry(scoreHolder).displayName = player -> display;

        modifyEntry(scoreHolder, e -> e.setDisplay(display));
    }

    @Override
    public void setNumberFormat(String scoreHolder, NumberFormat numberFormat) {
        getOrCreateEntry(scoreHolder).numberFormat = numberFormat;

        modifyEntry(scoreHolder, e -> e.setNumberFormat(numberFormat));
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

    public void unload() {
        eachObjective((player, objective) -> {
            CustomObjective.setDisplay(player, null, slot);
            objective.remove(player);
        });

        objectives.clear();
    }

    private static final class DynamicEntry {
        private final String holder;
        private int score;
        private NumberFormat numberFormat;
        private Function<ServerPlayerEntity, @Nullable Text> displayName;

        private DynamicEntry(String holder, int score, NumberFormat numberFormat, Function<ServerPlayerEntity, @Nullable Text> displayName) {
            this.holder = holder;
            this.score = score;
            this.numberFormat = numberFormat;
            this.displayName = displayName;
        }

        public void put(ServerPlayerEntity player, CustomObjective objective) {
            objective.setEntry(holder, createEntry(player));
        }

        public CustomScoreboardEntry createEntry(ServerPlayerEntity player) {
            Text display = displayName.apply(player);

            return new CustomScoreboardEntry(display, numberFormat, score);
        }
    }
}
