package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardPlayerUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboard;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class TranslatedScoreboardObjective implements CustomScoreboardObjective, Unloadable {

    private final TranslationService translations;
    private final CustomScoreboard scoreboard;
    private final PlayerManager playerManager;
    private final String name;
    private final ScoreboardCriterion.RenderType renderType;
    private final Map<ScoreboardObjective, Set<UUID>> objectivePlayers = new HashMap<>();
    private final Map<String, ScoreboardObjective> localizedObjectives = new HashMap<>();
    private final Map<UUID, String> players = new HashMap<>();
    private String translationKey;
    private Object[] args;
    private int slot = -1;
    private Style style = Style.EMPTY;

    public TranslatedScoreboardObjective(TranslationService translations, CustomScoreboard scoreboard, PlayerManager playerManager, String name,
                                         ScoreboardCriterion.RenderType renderType, String translationKey, Object[] args) {
        this.translations = translations;
        this.scoreboard = scoreboard;
        this.playerManager = playerManager;
        this.name = name;
        this.renderType = renderType;
        this.translationKey = translationKey;
        this.args = args;
    }

    public void addPlayer(ServerPlayerEntity player) {
        final String language = translations.getLanguage(player);
        final UUID uuid = player.getUuid();

        final String oldLanguage = players.get(uuid);

        // check if language did change
        if (language.equals(oldLanguage)) return;

        if (oldLanguage != null) {
            // the language changed, remove the player from the old boss bar
            removePlayer(player);
        }

        ScoreboardObjective objective = getLocalizedObjective(language);

        objectivePlayers.computeIfAbsent(objective, ignored -> new HashSet<>()).add(uuid);

        syncAddObjective(player, objective);
        syncDisplay(player, objective, slot);

        players.put(uuid, language);
    }

    public void removePlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String lang = players.remove(uuid);
        if (lang == null) return;

        ScoreboardObjective objective = localizedObjectives.get(lang);

        if (objective == null) return;

        syncDisplay(player, null, slot);
        syncRemoveObjective(player, objective);

        Set<UUID> uuids = objectivePlayers.get(objective);
        uuids.remove(uuid);
    }

    public void updatePlayerLanguage(ServerPlayerEntity player) {
        if (!players.containsKey(player.getUuid())) return;

        // adding the player will update the language
        addPlayer(player);
    }

    @NotNull
    private ScoreboardObjective getLocalizedObjective(String language) {
        return localizedObjectives.computeIfAbsent(language, this::createLocalizedObjective);
    }

    @NotNull
    private ScoreboardObjective createLocalizedObjective(String language) {
        Text localizedTitle = getLocalizedTitle(language);

        String suffix = language.replaceAll("[^a-zA-Z0-9._-]", "");  // remove invalid characters
        String localizedName = name + "_" + (suffix);

        return createObjective(localizedName, localizedTitle);
    }

    @NotNull
    private ScoreboardObjective createObjective(String name, Text title) {
        return scoreboard.createObjective(name, ScoreboardCriterion.DUMMY, title, renderType);
    }

    @NotNull
    private Text getLocalizedTitle(String language) {
        RootText rootText = translations.translateText(language, translationKey, args);
        rootText.setStyle(style);

        return rootText;
    }

    public void setDisplayName(String translationKey, Object... args) {
        this.translationKey = translationKey;
        this.args = args;

        for (var entry : localizedObjectives.entrySet()) {
            Text localizedTitle = getLocalizedTitle(entry.getKey());

            ScoreboardObjective objective = entry.getValue();
            objective.setDisplayName(localizedTitle);

            Set<UUID> uuids = objectivePlayers.get(objective);

            for (UUID uuid : uuids) {
                ServerPlayerEntity player = playerManager.getPlayer(uuid);
                if (player == null) continue;

                syncUpdateObjective(player, objective);
            }
        }
    }

    private void updateObjectives(Consumer<ScoreboardObjective> action) {
        for (var entry : localizedObjectives.entrySet()) {
            ScoreboardObjective objective = entry.getValue();
            action.accept(objective);
        }
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    /**
     * Updates the style of the title text.
     *
     * @see #getStyle()
     * @see #setStyle(Style)
     *
     * @param styleUpdater the style updater
     */
    public TranslatedScoreboardObjective styled(UnaryOperator<Style> styleUpdater) {
        this.setStyle(styleUpdater.apply(this.getStyle()));
        return this;
    }

    /**
     * Fills the absent parts of the title text's style with definitions from {@code styleOverride}.
     *
     * @see Style#withParent(Style)
     *
     * @param styleOverride the style that provides definitions for absent definitions in the title text's style
     */
    public TranslatedScoreboardObjective fillStyle(Style styleOverride) {
        this.setStyle(styleOverride.withParent(this.getStyle()));
        return this;
    }

    /**
     * Adds some formattings to the title text's style.
     *
     * @param formattings an array of formattings
     */
    public TranslatedScoreboardObjective formatted(Formatting... formattings) {
        this.setStyle(this.getStyle().withFormatting(formattings));
        return this;
    }

    /**
     * Add a formatting to the title text's style.
     *
     * @param formatting a formatting
     */
    public TranslatedScoreboardObjective formatted(Formatting formatting) {
        this.setStyle(this.getStyle().withFormatting(formatting));
        return this;
    }

    public void setSlot(int slot) {
        if (slot == this.slot) return;

        int prevSlot = this.slot;
        this.slot = slot;

        for (var entry : players.entrySet()) {
            ServerPlayerEntity player = playerManager.getPlayer(entry.getKey());

            if (player == null) continue;

            if (prevSlot >= 0) {
                syncDisplay(player, null, prevSlot);
            }

            if (slot < 0) continue;  // hidden

            String lang = entry.getValue();
            ScoreboardObjective objective = localizedObjectives.get(lang);

            if (objective == null) continue;

            syncDisplay(player, objective, slot);
        }
    }

    private void syncDisplay(ServerPlayerEntity player, @Nullable ScoreboardObjective objective, int slot) {
        if (slot < 0) return;

        // could be that ScoreboardObjectiveUpdateS2CPacket with ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE has to be sent
        var packet = new ScoreboardDisplayS2CPacket(slot, objective);
        player.networkHandler.sendPacket(packet);
    }

    @Override
    public void setScore(ServerPlayerEntity player, int score) {
        updateObjectives(objective -> {
            ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(player.getEntityName(), objective);
            playerScore.setScore(score);

            syncScore(playerScore);
        });
    }

    private void syncAddObjective(ServerPlayerEntity player, ScoreboardObjective objective) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE);
        player.networkHandler.sendPacket(packet);
    }

    private void syncRemoveObjective(ServerPlayerEntity player, ScoreboardObjective objective) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE);
        player.networkHandler.sendPacket(packet);
    }

    private void syncUpdateObjective(ServerPlayerEntity player, ScoreboardObjective objective) {
        var packet = new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE);
        player.networkHandler.sendPacket(packet);
    }

    private void syncScore(ScoreboardPlayerScore playerScore) {
        ScoreboardObjective objective = playerScore.getObjective();
        if (objective == null) return;

        Set<UUID> uuids = objectivePlayers.get(objective);
        if (uuids == null) return;

        var packet = new ScoreboardPlayerUpdateS2CPacket(ServerScoreboard.UpdateMode.CHANGE, objective.getName(),
                playerScore.getPlayerName(), playerScore.getScore());

        for (UUID uuid : uuids) {
            ServerPlayerEntity player = playerManager.getPlayer(uuid);
            if (player == null) continue;

            player.networkHandler.sendPacket(packet);
        }
    }

    @Override
    public void unload() {
        objectivePlayers.forEach((objective, uuids) -> {
            for (UUID uuid : uuids) {
                ServerPlayerEntity player = playerManager.getPlayer(uuid);
                if (player == null) continue;

                syncRemoveObjective(player, objective);
            }
        });
    }
}
