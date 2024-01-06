package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.*;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;
import work.lclpnet.kibu.translate.util.WeakList;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.HashSet;
import java.util.Set;

public class CustomScoreboardManager implements Unloadable {

    private final ServerScoreboard scoreboard;
    private final TranslationService translations;
    private final PlayerManager playerManager;
    private final Set<Team> teams = new HashSet<>();
    private final Set<ScoreboardObjective> objectives = new HashSet<>();
    private final WeakList<TranslatedScoreboardObjective> translatedObjectives = new WeakList<>();

    public CustomScoreboardManager(ServerScoreboard scoreboard, TranslationService translations, PlayerManager playerManager) {
        this.scoreboard = scoreboard;
        this.translations = translations;
        this.playerManager = playerManager;
    }

    public void init(HookRegistrar hookRegistrar) {
        hookRegistrar.registerHook(LanguageChangedCallback.HOOK, (player, language, reason) -> {
            for (TranslatedScoreboardObjective objective : translatedObjectives) {
                objective.updatePlayerLanguage(player);
            }
        });

        hookRegistrar.registerHook(PlayerConnectionHooks.JOIN, player -> {
            for (TranslatedScoreboardObjective objective : translatedObjectives) {
                objective.addPlayer(player);
            }
        });
    }

    public void joinTeam(Entity player, Team team) {
        scoreboard.addPlayerToTeam(player.getEntityName(), team);
    }

    public void joinTeam(Iterable<? extends Entity> players, Team team) {
        for (Entity entity : players) {
            joinTeam(entity, team);
        }
    }

    public Team createTeam(String name) {
        removeTeam(name);

        Team team = scoreboard.addTeam(name);

        synchronized (this) {
            teams.add(team);
        }

        return team;
    }

    private void removeTeam(String name) {
        Team team = scoreboard.getTeam(name);

        if (team == null) return;

        scoreboard.removeTeam(team);

        synchronized (this) {
            teams.remove(team);
        }
    }

    public ScoreboardObjective createObjective(String name, ScoreboardCriterion criterion, Text displayName,
                                               ScoreboardCriterion.RenderType renderType) {
        removeObjective(name);

        ScoreboardObjective objective = scoreboard.addObjective(name, criterion, displayName, renderType);

        synchronized (this) {
            objectives.add(objective);
        }

        return objective;
    }

    private void removeObjective(String name) {
        ScoreboardObjective objective = scoreboard.getObjective(name);

        if (objective == null) return;

        scoreboard.removeObjective(objective);

        synchronized (this) {
            objectives.remove(objective);
        }
    }

    public void setScore(ServerPlayerEntity player, ScoreboardObjective objective, int score) {
        if (!objectives.contains(objective)) return;  // objective is not associated with this instance

        ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(player.getEntityName(), objective);
        playerScore.setScore(score);
    }

    public void setDisplay(int slot, ScoreboardObjective objective) {
        scoreboard.setObjectiveSlot(slot, objective);
    }

    public void sync(ScoreboardObjective objective, IntScoreEventSource source) {
        source.register((player, score) -> setScore(player, objective, score));
    }

    public void sync(CustomScoreboardObjective objective, IntScoreEventSource source) {
        source.register(objective::setScore);
    }

    public TranslatedScoreboardObjective translateObjective(String name, ScoreboardCriterion.RenderType renderType,
                                                            String translationKey, Object... args) {
        var objective = new TranslatedScoreboardObjective(translations, playerManager, name, renderType, translationKey, args);

        translatedObjectives.add(objective);

        return objective;
    }

    @Override
    public void unload() {
        synchronized (this) {
            teams.forEach(scoreboard::removeTeam);
            teams.clear();

            translatedObjectives.forEach(Unloadable::unload);

            objectives.forEach(scoreboard::removeObjective);
            objectives.clear();
        }
    }
}
