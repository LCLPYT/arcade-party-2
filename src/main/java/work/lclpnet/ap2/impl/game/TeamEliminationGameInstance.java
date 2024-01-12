package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;

import static net.minecraft.util.Formatting.GRAY;

public abstract class TeamEliminationGameInstance extends DefaultTeamGameInstance {

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
        TranslationService translations = gameHandle.getTranslations();

        if (participants.isParticipating(player)) {
            translations.translateText("ap2.game.eliminated", player.getDisplayName())
                    .formatted(GRAY)
                    .sendTo(PlayerLookup.all(gameHandle.getServer()));

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
            teamManager.setTeamEliminated(team);
        }

        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : team.getPlayers()) {
            participants.remove(player);
            resetPlayer(player);
        }
    }
}
