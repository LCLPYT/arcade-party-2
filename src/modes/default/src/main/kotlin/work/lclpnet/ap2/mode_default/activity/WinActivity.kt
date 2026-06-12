package work.lclpnet.ap2.mode_default.activity;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.impl.game.Announcer;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.ResultAnnouncement;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.mode_default.util.ApBaseArgs;
import work.lclpnet.ap2.mode_default.util.BaseActivityConfigurator;
import work.lclpnet.ap2.mode_default.util.ScoreManager;
import work.lclpnet.ap2.util.FontService;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;
import work.lclpnet.game.util.ProtectorComponent;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;

import java.util.Collection;
import java.util.List;
import java.util.Random;

import static net.minecraft.ChatFormatting.GRAY;
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
    private final FontService font;
    private final Random random = new Random();
    private Scheduler scheduler;
    private ServerLevel world;
    private GameMap map;

    public WinActivity(ApBaseArgs args) {
        super(args.miniGameArgs().server(), args.miniGameArgs().logger());

        this.args = args;
        this.activityConfigurator = new BaseActivityConfigurator(this, args);
        this.translations = args.miniGameArgs().translations();
        this.font = args.miniGameArgs().fontService();
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

        args.tablistManager().setStatus(args.miniGameArgs().translations().translateText("ap2.status.game_over"));
        args.tablistManager().update();

        PreparationActivity.setupMap(args.miniGameArgs())
                .whenComplete((res, err) -> {
                    if (err != null) {
                        args.miniGameArgs().logger().error("Failed to setup win activity map", err);
                    } else {
                        onReady(res.world(), res.map());
                    }
                });
    }

    private void onReady(ServerLevel world, GameMap map) {
        this.world = world;
        this.map = map;

        activityConfigurator.configureProtector();
        activityConfigurator.configureHooks();

        world.getWaypointManager().breakAllConnections();

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

        for (ServerPlayer player : players()) {
            Title.get(player).title(
                    winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA),
                    Component.empty(),
                    5,
                    50,
                    0
            );
        }

        playSound(world, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        playSound(world, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.RECORDS, 0.8f, 0f);

        beginFireworks().then(this::onFireworksOver);

        scheduler.timeout(() -> {
            for (ServerPlayer player : players()) {
                Title.get(player).title(
                        winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA),
                        translations.translateText(player, "ap2.awards.won_party").formatted(ChatFormatting.DARK_GREEN),
                        0, 100, 5
                );
            }
        }, 40);

        scheduler.timeout(this::announceStats, STATS_ANNOUNCE_DELAY_TICKS);
    }

    private Action<Runnable> beginFireworks() {
        Vec3 spawn = MapUtils.getSpawnPosition(map);
        var fireworks = new Fireworks(world, spawn, 30.d, random);

        return fireworks.start(scheduler, FIREWORKS_DURATION_TICKS, FIREWORKS_MIN_DELAY_TICKS, FIREWORKS_MAX_DELAY_TICKS);
    }

    private void announceStats() {
        playSound(world, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0.5f);

        ScoreManager scoreManager = args.scoreManager();

        List<ObjectIntPair<PlayerRef>> order = scoreManager.streamEntriesRanked()
                .flatMap(Collection::stream)
                .toList();

        var announcement = new ResultAnnouncement<>(translations, font, PlayerRef::create, order, scoreManager::getEntry);

        for (ServerPlayer player : players()) {
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

    private Iterable<ServerPlayer> players() {
        return world != null ? PlayerLookup.level(world) : List.of();
    }
}
