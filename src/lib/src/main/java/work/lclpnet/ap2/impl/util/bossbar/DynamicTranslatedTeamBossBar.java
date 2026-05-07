package work.lclpnet.ap2.impl.util.bossbar;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;
import work.lclpnet.kibu.hook.HookRegistrar;

public class DynamicTranslatedTeamBossBar implements PlayerBossBar {

    private final DynamicTranslatedPlayerBossBar delegate;
    private final TeamManager teamManager;
    private final Object2FloatMap<Team> percent = new Object2FloatOpenHashMap<>();

    public DynamicTranslatedTeamBossBar(DynamicTranslatedPlayerBossBar delegate, TeamManager teamManager) {
        this.delegate = delegate;
        this.teamManager = teamManager;
    }

    @Override
    public ServerBossEvent getBossBar(ServerPlayer player) {
        ServerBossEvent bossBar = delegate.getBossBar(player);

        teamManager.getTeam(player).ifPresent(team -> {
            float percent = getPercent(team);
            bossBar.setProgress(percent);
        });

        return bossBar;
    }

    @Override
    public void remove(ServerPlayer player) {
        delegate.remove(player);
    }

    public void init(HookRegistrar hooks) {
        delegate.init(hooks);
    }

    public void setTranslationKey(Team team, String translationKey) {
        for (ServerPlayer player : team.getPlayers()) {
            delegate.setTranslationKey(player, translationKey);
        }
    }

    public void setArguments(Team team, Object[] arguments) {
        for (ServerPlayer player : team.getPlayers()) {
            delegate.setArguments(player, arguments);
        }
    }

    public void setArgument(Team team, int i, Object argument) {
        for (ServerPlayer player : team.getPlayers()) {
            delegate.setArgument(player, i, argument);
        }
    }
    public DynamicTranslatedPlayerBossBar getDelegate() {
        return delegate;
    }

    public float getPercent(Team team) {
        return percent.getOrDefault(team, 0f);
    }

    public void setPercent(Team team, float percent) {
        for (ServerPlayer player : team.getPlayers()) {
            ServerBossEvent bossBar = delegate.getBossBar(player);
            bossBar.setProgress(percent);

            this.percent.put(team, percent);
        }
    }
}
