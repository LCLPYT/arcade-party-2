package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;

import java.util.HashSet;
import java.util.Set;

import static net.minecraft.util.Formatting.GRAY;

public abstract class TeamEliminationGameInstance extends DefaultTeamGameInstance {

    private final EliminationDataContainer<Team, TeamRef> data = new EliminationDataContainer<>(this::createReference);

    public TeamEliminationGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected final void useSmoothDeath() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            if (!(entity instanceof ServerPlayerEntity player) || health > 0) return false;

            eliminate(player);

            return true;
        });
    }

    protected void eliminate(ServerPlayerEntity player) {
        Participants participants = gameHandle.getParticipants();

        if (participants.isParticipating(player)) {
            DeathMessages deathMessages = gameHandle.getDeathMessages();
            MinecraftServer server = gameHandle.getServer();

            deathMessages.eliminated(player).sendTo(PlayerLookup.all(server));

            participants.remove(player);
        }

        resetPlayer(player);
    }

    private void resetPlayer(ServerPlayerEntity player) {
        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.resetPlayer(player);
        worldFacade.teleport(player);
    }

    protected void eliminate(Team team) {
        TeamManager teamManager = getTeamManager();

        if (teamManager.isParticipating(team)) {
            DeathMessages deathMessages = gameHandle.getDeathMessages();
            MinecraftServer server = gameHandle.getServer();

            deathMessages.eliminated(team).sendTo(PlayerLookup.all(server));

            teamManager.setTeamEliminated(team);
        }

        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : team.getPlayers()) {
            participants.remove(player);
            resetPlayer(player);
        }
    }

    protected void eliminateAll(Iterable<? extends Team> teams) {
        TeamManager teamManager = getTeamManager();
        TranslationService translations = gameHandle.getTranslations();

        Set<Team> toEliminate = new HashSet<>();

        for (Team team : teams) {
            if (!teamManager.isParticipating(team)) continue;

            TeamKey key = team.getKey();
            var displayName = translations.translateText(key.getTranslationKey()).formatted(key.colorFormat());

            translations.translateText("ap2.game.team_eliminated", displayName)
                    .formatted(GRAY)
                    .sendTo(PlayerLookup.all(gameHandle.getServer()));

            toEliminate.add(team);
        }

        // mark all teams as eliminated at the same moment
        data.allEliminated(toEliminate);

        toEliminate.forEach(teamManager::setTeamEliminated);

        Participants participants = gameHandle.getParticipants();

        // deliberately use teams instead of toEliminate, to make sure there are no participating members anymore
        for (Team team : teams) {
            for (ServerPlayerEntity player : team.getPlayers()) {
                participants.remove(player);
                resetPlayer(player);
            }
        }
    }

    @Override
    public void teamEliminated(Team team) {
        // make sure the team is tracked as eliminated
        data.eliminated(team);

        super.teamEliminated(team);
    }

    @Override
    public DataContainer<Team, TeamRef> getData() {
        return data;
    }
}
