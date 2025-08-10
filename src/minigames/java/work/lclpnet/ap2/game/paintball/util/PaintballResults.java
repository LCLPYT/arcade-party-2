package work.lclpnet.ap2.game.paintball.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.game.Announcer;
import work.lclpnet.ap2.impl.game.WinManager;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.title.AnimatedTitle;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class PaintballResults {

    private static final int RESULT_DELAY_TICKS = Ticks.seconds(3);

    private final MiniGameHandle gameHandle;
    private final Announcer announcer;
    private final ServerWorld world;
    private final ResultSpot resultSpot;
    private final Supplier<List<TeamRef>> teamRefs;
    private final IntScoreDataContainer<Team, TeamRef> data;
    private final WinManager<Team, TeamRef> winManager;

    public PaintballResults(MiniGameHandle gameHandle, Announcer announcer, ServerWorld world, ResultSpot resultSpot,
                            IntScoreDataContainer<Team, TeamRef> data, WinManager<Team, TeamRef> winManager,
                            Supplier<List<TeamRef>> teamRefs) {
        this.gameHandle = gameHandle;
        this.announcer = announcer;
        this.world = world;
        this.resultSpot = resultSpot;
        this.teamRefs = teamRefs;
        this.data = data;
        this.winManager = winManager;
    }

    public void beginResults() {
        gameHandle.resetGameScheduler();

        teleportPlayersToResults();

        gameHandle.getGameScheduler().interval(this::teleportPlayersToResults, 1);

        announcer.announce("game.ap2.paintball.game_over", null);

        gameHandle.getGameScheduler().timeout(this::showResults, RESULT_DELAY_TICKS);
    }

    private void teleportPlayersToResults() {
        Vec3d pos = resultSpot.pos;

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            player.changeGameMode(GameMode.SPECTATOR);
            player.getAbilities().setFlySpeed(0);
            player.sendAbilitiesUpdate();

            player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), resultSpot.yaw, resultSpot.pitch, true);
        }
    }

    private void showResults() {
        var animatedTitle = new AnimatedTitle();

        animatedTitle.add(new PaintballResultAnimation(teamRefs.get(), data, gameHandle.getServer(), gameHandle.getTranslations(), () -> {
            for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
                player.changeGameMode(GameMode.SPECTATOR);
                player.getAbilities().setFlySpeed(0.05f);
                player.sendAbilitiesUpdate();
            }

            winManager.complete();
        }));

        animatedTitle.start(gameHandle.getGameScheduler(), 1);

        gameHandle.whenDone(animatedTitle::stop);
    }

    public record ResultSpot(Vec3d pos, float yaw, float pitch) {

        public static ResultSpot fromJson(JSONObject json) {
            Vec3d pos = MapUtil.readCenteredVec3d(json.getJSONArray("pos"));
            float yaw = MapUtil.readAngle(json.optNumber("yaw", 0));
            float pitch = MapUtil.readAngle(json.optNumber("pitch", 0));

            return new ResultSpot(pos, yaw, pitch);
        }
    }
}
