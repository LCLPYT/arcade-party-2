package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.*;
import work.lclpnet.ap2.impl.game.team.SimpleTeamManager;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Collections;
import java.util.Map;

import static net.minecraft.util.Formatting.GRAY;

public abstract class DefaultTeamGameInstance extends BaseGameInstance implements ParticipantListener,
        TeamEliminatedListener, TeamSpawnAccess {

    private volatile SimpleTeamManager teamManager = null;
    private volatile Map<String, PositionRotation> teamSpawns = null;

    public DefaultTeamGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        if (teamManager == null) return;

        Team team = teamManager.getTeam(player).orElse(null);

        if (team == null || !teamManager.isParticipating(team)
            || !team.getParticipatingPlayers(gameHandle.getParticipants()).isEmpty()) return;

        teamManager.setTeamEliminated(team);
    }

    @Override
    public void teamEliminated(Team team) {
        TranslationService translations = gameHandle.getTranslations();
        TeamKey key = team.getKey();
        var displayName = translations.translateText(key.getTranslationKey()).formatted(key.colorFormat());

        translations.translateText("ap2.game.team_eliminated", displayName)
                .formatted(GRAY)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

        var participatingTeams = teamManager.getParticipatingTeams();

        if (participatingTeams.size() > 1) return;

        var winningTeam = participatingTeams.stream().findAny();

        // TODO if winningTeam is empty, try to get the winning team from the data container

        // TODO implement proper winning effects

        winningTeam.map(Team::getPlayers).ifPresentOrElse(gameHandle::complete, gameHandle::completeWithoutWinner);
    }

    @NotNull
    protected final TeamManager getTeamManager() {
        if (teamManager != null) return teamManager;

        synchronized (this) {
            if (teamManager != null) return teamManager;

            PlayerManager playerManager = gameHandle.getServer().getPlayerManager();
            TeamConfig teamConfig = gameHandle.getTeamConfig().orElseGet(TeamConfig::defaultConfig);
            CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

            teamManager = new SimpleTeamManager(playerManager, teamConfig, scoreboardManager);
        }

        teamManager.init(gameHandle.getHookRegistrar());
        teamManager.bind(this);

        return teamManager;
    }

    protected final Team getTeam(ServerPlayerEntity player) {
        return getTeamManager().getTeam(player).orElseThrow();
    }

    protected final void teleportTeamsToSpawns() {
        ServerWorld world = getWorld();

        for (Team team : teamManager.getTeams()) {
            PositionRotation spawn = getSpawn(team);

            if (spawn == null) {
                gameHandle.getLogger().error("No spawn configured for team {} in map {}", team.getKey().id(), getMap().getDescriptor().getIdentifier());
                continue;
            }

            double x = spawn.getX(), y = spawn.getY(), z = spawn.getZ();
            float yaw = spawn.getYaw(), pitch = spawn.getPitch();

            for (ServerPlayerEntity player : team.getPlayers()) {
                player.teleport(world, x, y, z, yaw, pitch);
            }
        }
    }

    @Nullable
    public final PositionRotation getSpawn(Team team) {
        return getSpawns().get(team.getKey().id());
    }

    private Map<String, PositionRotation> getSpawns() {
        if (teamSpawns != null) {
            return teamSpawns;
        }

        synchronized (this) {
            if (teamSpawns != null) return teamSpawns;

            var spawns = MapUtils.getNamedSpawnPositionsAndRotation(getMap());
            teamSpawns = Collections.unmodifiableMap(spawns);
        }

        return teamSpawns;
    }
}
