package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.scoreboard.AbstractTeam;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;

import java.util.Set;

public class CozyCampfireInstance extends TeamEliminationGameInstance {

    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;

    public CozyCampfireInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useOldCombat();
    }

    @Override
    protected void prepare() {
        TeamManager teamManager = getTeamManager();
        teamManager.partitionIntoTeams(gameHandle.getParticipants(), Set.of(TEAM_RED, TEAM_BLUE));
        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        teleportToTeamSpawns();
    }

    @Override
    protected void ready() {

    }
}
