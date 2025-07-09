package work.lclpnet.ap2.base.activity;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.base.util.ApBaseArgs;
import work.lclpnet.ap2.base.util.BaseActivityConfigurator;
import work.lclpnet.ap2.base.util.ScoreManager;
import work.lclpnet.ap2.impl.game.Announcer;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.ResultAnnouncement;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.game.util.ProtectorComponent;

import java.util.Collection;
import java.util.List;
import java.util.Random;

import static net.minecraft.util.Formatting.GRAY;
import static work.lclpnet.ap2.impl.util.SoundHelper.playSound;

public class WinActivity extends ComponentActivity {

    private static final int
            WINNER_ANNOUNCE_DELAY_TICKS = Ticks.seconds(5),
            STATS_ANNOUNCE_DELAY_TICKS = Ticks.seconds(8),
            FIREWORKS_DURATION_TICKS = Ticks.seconds(15),
            FINAL_DELAY_TICKS = Ticks.seconds(10),
            FIREWORKS_MIN_DELAY_TICKS = 3,
            FIREWORKS_MAX_DELAY_TICKS = 8;

    private final ApBaseArgs args;
    private final BaseActivityConfigurator activityConfigurator;
    private final Announcer announcer;
    private final Translations translations;
    private final Random random = new Random();
    private Scheduler scheduler;
    private ServerWorld world;
    private GameMap map;

    public WinActivity(ApBaseArgs args) {
        super(args.miniGameArgs().server(), args.miniGameArgs().logger());

        this.args = args;
        this.activityConfigurator = new BaseActivityConfigurator(this, args);
        this.translations = args.miniGameArgs().translations();
        this.announcer = new Announcer(translations, this::players);
    }

    @Override
    protected void registerComponents(ComponentBundle components) {
        components
                .add(BuiltinComponents.HOOKS)
                .add(BuiltinComponents.SCHEDULER)
                .add(ProtectorComponent.KEY);
    }

    @Override
    public void start() {
        super.start();

        scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        PreparationActivity.setupMap(args.miniGameArgs(), this::onReady);
    }

    private void onReady(ServerWorld world, GameMap map) {
        this.world = world;
        this.map = map;

        activityConfigurator.configureProtector();
        activityConfigurator.configureHooks();

        world.getWaypointHandler().clear();

        args.playerManager().leaveFinale();

        activityConfigurator.resetPlayers();

        scheduler.timeout(this::afterInitialDelay, PlayerUtil.getLoadingDelayTicks(args.playerManager().count()));
    }

    private void afterInitialDelay() {
        announcer.withTimes(5, WINNER_ANNOUNCE_DELAY_TICKS - 40, 5)
                .announceSubtitle("ap2.awards.winner_decided");

        scheduler.timeout(this::announceWinner, WINNER_ANNOUNCE_DELAY_TICKS);
    }

    private void announceWinner() {
        PlayerRef winner = args.scoreManager().getFinalWinner().orElseThrow();

        for (ServerPlayerEntity player : players()) {
            Title.get(player).title(Text.literal(winner.name()).formatted(Formatting.AQUA), Text.empty(), 5, 50, 0);
        }

        playSound(world, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
        playSound(world, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 0.8f, 0f);

        beginFireworks().then(this::onFireworksOver);

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : players()) {
                Title.get(player).title(
                        Text.literal(winner.name()).formatted(Formatting.AQUA),
                        translations.translateText(player, "ap2.awards.won_party").formatted(Formatting.DARK_GREEN),
                        0, 100, 5
                );
            }
        }, 40);

        scheduler.timeout(this::announceStats, STATS_ANNOUNCE_DELAY_TICKS);
    }

    private Action<Runnable> beginFireworks() {
        Vec3d spawn = MapUtils.getSpawnPosition(map);
        var fireworks = new Fireworks(world, spawn, 30.d, random);

        return fireworks.start(scheduler, FIREWORKS_DURATION_TICKS, FIREWORKS_MIN_DELAY_TICKS, FIREWORKS_MAX_DELAY_TICKS);
    }

    private void announceStats() {
        playSound(world, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 1f, 0.5f);

        ScoreManager scoreManager = args.scoreManager();

        List<ObjectIntPair<PlayerRef>> order = scoreManager.streamEntriesRanked()
                .flatMap(Collection::stream)
                .toList();

        var announcement = new ResultAnnouncement<>(translations, PlayerRef::create, order, scoreManager::getEntry);

        for (ServerPlayerEntity player : players()) {
            announcement.sendTop(5, player);
        }
    }

    private void onFireworksOver() {
        translations.translateText("ap2.awards.thanks").formatted(GRAY).sendTo(players());

        scheduler.timeout(this::endGame, FINAL_DELAY_TICKS);
    }

    private void endGame() {
        args.finisher().finishGame();
    }

    private Iterable<ServerPlayerEntity> players() {
        return world != null ? PlayerLookup.world(world) : List.of();
    }
}
