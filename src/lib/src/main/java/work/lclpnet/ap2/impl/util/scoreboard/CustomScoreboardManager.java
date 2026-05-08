package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.api.util.scoreboard.VirtualScoreboardObjective;
import work.lclpnet.ap2.core.type.ApServerPlayerEntity;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CustomScoreboardManager {

    private final ServerScoreboard scoreboard;
    @Getter
    private final Translations translations;
    private final PlayerList playerManager;
    private final Set<PlayerTeam> teams = new HashSet<>();
    private final Set<Objective> objectives = new HashSet<>();
    private final List<VirtualScoreboardObjective> virtualObjectives = new ArrayList<>();

    public CustomScoreboardManager(ServerScoreboard scoreboard, Translations translations, PlayerList playerManager) {
        this.scoreboard = scoreboard;
        this.translations = translations;
        this.playerManager = playerManager;
    }

    public void init(HookRegistrar hookRegistrar) {
        LanguageChangedCallback.HOOK.registerWith(hookRegistrar, (player, _, _) -> {
            for (var objective : virtualObjectives) {
                objective.update(player);
            }
        });

        PlayerConnectionHooks.JOIN.registerWith(hookRegistrar, player -> {
            for (var objective : virtualObjectives) {
                objective.add(player);
            }
        });

        PlayerConnectionHooks.QUIT.registerWith(hookRegistrar, player -> {
            PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (team != null) leaveTeam(player, team);
        });
    }

    public void joinTeam(Entity entity, PlayerTeam team) {
        scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);

        // manually set team color as teams themselves don't support arbitrary text color
        if (entity instanceof ServerPlayer player) {
            ((ApServerPlayerEntity) player).ap2$setPlayerListName(player.getDisplayName());
        }
    }

    public void leaveTeam(Entity entity, PlayerTeam team) {
        String entityName = entity.getScoreboardName();

        if (scoreboard.getPlayersTeam(entityName) != team) return;

        scoreboard.removePlayerFromTeam(entityName, team);
    }

    public void joinTeam(Iterable<? extends Entity> players, PlayerTeam team) {
        for (Entity entity : players) {
            joinTeam(entity, team);
        }
    }

    public PlayerTeam createTeam(String name) {
        removeTeam(name);

        PlayerTeam team = scoreboard.addPlayerTeam(name);

        synchronized (this) {
            teams.add(team);
        }

        return team;
    }

    public void removeTeam(String name) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);

        if (team == null) return;

        scoreboard.removePlayerTeam(team);

        synchronized (this) {
            teams.remove(team);
        }
    }

    public Objective createObjective(String name, ObjectiveCriteria criterion, Component displayName,
                                     ObjectiveCriteria.RenderType renderType) {
        return createObjective(name, criterion, displayName, renderType, StyledFormat.SIDEBAR_DEFAULT);
    }

    public Objective createObjective(String name, ObjectiveCriteria criterion, Component displayName,
                                     ObjectiveCriteria.RenderType renderType, NumberFormat numberFormat) {
        removeObjective(name);

        Objective objective = scoreboard.addObjective(name, criterion, displayName, renderType,
                true, numberFormat);

        synchronized (this) {
            objectives.add(objective);
        }

        return objective;
    }

    private void removeObjective(String name) {
        Objective objective = scoreboard.getObjective(name);

        if (objective == null) return;

        scoreboard.removeObjective(objective);

        synchronized (this) {
            objectives.remove(objective);
        }
    }

    public void setScore(ScoreHolder player, Objective objective, int score) {
        ScoreAccess playerScore = getOrCreateScore(player, objective);

        if (playerScore == null) return;

        playerScore.set(score);
    }

    public void removeScore(ScoreHolder holder, Objective objective) {
        scoreboard.resetSinglePlayerScore(holder, objective);
    }

    public void setNumberFormat(ScoreHolder holder, Objective objective, @Nullable NumberFormat format) {
        ScoreAccess playerScore = getOrCreateScore(holder, objective);

        if (playerScore == null) return;

        playerScore.numberFormatOverride(format);
    }

    public void setDisplayText(ScoreHolder holder, Objective objective, @Nullable Component text) {
        ScoreAccess playerScore = getOrCreateScore(holder, objective);

        if (playerScore == null) return;

        playerScore.display(text);
    }

    @Nullable
    public ScoreAccess getOrCreateScore(ScoreHolder holder, Objective objective) {
        if (!objectives.contains(objective)) return null;  // objective is not associated with this instance

        return scoreboard.getOrCreatePlayerScore(holder, objective);
    }

    public void setDisplay(DisplaySlot slot, Objective objective) {
        scoreboard.setDisplayObjective(slot, objective);
    }

    public void sync(Objective objective, IntScoreEventSource<ServerPlayer> source) {
        source.register((player, score) -> setScore(player, objective, score));
    }

    public void sync(CustomScoreboardObjective objective, IntScoreEventSource<ServerPlayer> source) {
        source.register(objective::setScore);
    }

    public TranslatedScoreboardObjective translateObjective(String name, String translationKey, Object... args) {
        return translateObjective(name, ObjectiveCriteria.RenderType.INTEGER, translationKey, args);
    }

    public TranslatedScoreboardObjective translateObjective(String name, ObjectiveCriteria.RenderType renderType,
                                                            String translationKey, Object... args) {
        var objective = new TranslatedScoreboardObjective(translations, playerManager, name, renderType, translationKey, args);

        virtualObjectives.add(objective);

        return objective;
    }

    public DynamicScoreboardObjective createDynamicObjective(String name, Function<ServerPlayer, Component> title) {
        return createDynamicObjective(name, ObjectiveCriteria.RenderType.INTEGER, title);
    }

    public DynamicScoreboardObjective createDynamicObjective(String name, ObjectiveCriteria.RenderType renderType,
                                                             Function<ServerPlayer, Component> title) {
        var objective = new DynamicScoreboardObjective(name, renderType, title, playerManager);

        virtualObjectives.add(objective);

        return objective;
    }

    public synchronized void unload() {
        teams.forEach(scoreboard::removePlayerTeam);
        teams.clear();

        virtualObjectives.forEach(VirtualScoreboardObjective::unload);

        objectives.forEach(scoreboard::removeObjective);
        objectives.clear();
    }
}
