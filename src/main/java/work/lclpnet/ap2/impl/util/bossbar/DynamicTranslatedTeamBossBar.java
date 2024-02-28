package work.lclpnet.ap2.impl.util.bossbar;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

public class DynamicTranslatedTeamBossBar implements PlayerBossBar {

    private final DynamicTranslatedPlayerBossBar delegate;
    private final TeamManager teamManager;
    private final Object2FloatMap<Team> percent = new Object2FloatOpenHashMap<>();

    public DynamicTranslatedTeamBossBar(DynamicTranslatedPlayerBossBar delegate, TeamManager teamManager) {
        this.delegate = delegate;
        this.teamManager = teamManager;
    }

    @Override
    public ServerBossBar getBossBar(ServerPlayerEntity player) {
        ServerBossBar bossBar = delegate.getBossBar(player);

        teamManager.getTeam(player).ifPresent(team -> {
            float percent = getPercent(team);
            bossBar.setPercent(percent);
        });

        return bossBar;
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        delegate.remove(player);
    }

    public void init(HookRegistrar hooks) {
        delegate.init(hooks);
    }

    public void setTranslationKey(Team team, String translationKey) {
        for (ServerPlayerEntity player : team.getPlayers()) {
            delegate.setTranslationKey(player, translationKey);
        }
    }

    public void setArguments(Team team, Object[] arguments) {
        for (ServerPlayerEntity player : team.getPlayers()) {
            delegate.setArguments(player, arguments);
        }
    }

    public void setArgument(Team team, int i, Object argument) {
        for (ServerPlayerEntity player : team.getPlayers()) {
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
        for (ServerPlayerEntity player : team.getPlayers()) {
            ServerBossBar bossBar = delegate.getBossBar(player);
            bossBar.setPercent(percent);

            this.percent.put(team, percent);
        }
    }
}
