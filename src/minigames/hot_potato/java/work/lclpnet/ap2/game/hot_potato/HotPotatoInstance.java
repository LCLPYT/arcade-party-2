package work.lclpnet.ap2.game.hot_potato;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.GameOverListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;

import java.util.List;
import java.util.Random;

import static net.minecraft.world.effect.MobEffects.GLOWING;

public class HotPotatoInstance extends EliminationGameInstance implements GameOverListener {

    public static final int
            DURATION_SECONDS = 20,
            MARK_PERIOD_TICKS = Ticks.seconds(6);

    private final Random random = new Random();
    private DynamicTranslatedBossBar dynamicBossBar;
    private ServerPlayer markedPlayer = null;
    private PlayerTeam team;
    private TaskHandle task = null, markTask = null;

    public HotPotatoInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        winManager.addListener(this);

        dynamicBossBar = useRemainingPlayersDisplay();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        team = scoreboardManager.createTeam("team");
        team.setColor(ChatFormatting.DARK_RED);
    }

    @Override
    protected void go() {
        nextRound();

        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(PlayerInteractionHooks.ATTACK_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && entity instanceof ServerPlayer hitPlayer) {
                tryPassPotato(serverPlayer, hitPlayer);
            }

            return InteractionResult.PASS;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && entity instanceof ServerPlayer hitPlayer) {
                tryPassPotato(serverPlayer, hitPlayer);
            }

            return InteractionResult.PASS;
        });
    }

    private void nextRound() {
        if (!markRandomPlayer()) {
            winManager.cancel();
            return;
        }

        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (player == markedPlayer) continue;

            glow(player, Ticks.seconds(3));
        }

        TranslatedBossBar bossBar = dynamicBossBar.getBossBar();
        TaskScheduler scheduler = gameHandle.getScheduler();

        markAndReschedule(scheduler);

        task = scheduler.interval(new SchedulerAction() {
            int t = 0, i = DURATION_SECONDS;

            @Override
            public void run(RunningTask info) {
                t++;

                spawnParticles();

                if (t < 20) return;

                t = 0;
                i--;

                bossBar.setProgress(Mth.clamp((float) i / DURATION_SECONDS, 0f, 1f));

                if (i > 0) {
                    spawnFirework(new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false), 1);
                    return;
                }

                spawnFirework(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(0xff0000), IntList.of(0xfff200), false, true), 0);

                info.cancel();

                eliminate(markedPlayer);

                bossBar.setProgress(1f);
            }
        }, 1).whenComplete(this::onRoundOver);
    }

    private void markAndReschedule(TaskScheduler scheduler) {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            glow(player, Ticks.seconds(1));
        }

        markTask = scheduler.timeout(() -> markAndReschedule(scheduler), MARK_PERIOD_TICKS);
    }

    private static void glow(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(GLOWING, ticks, 1, false, false, false));
    }

    private void onRoundOver() {
        if (markTask != null) {
            markTask.cancel();
        }

        for (ServerPlayer player : gameHandle.getParticipants()) {
            player.removeEffect(GLOWING);
        }

        if (winManager.isGameOver()) return;

        gameHandle.getScheduler().timeout(this::nextRound, Ticks.seconds(3));
    }

    @Override
    public void eliminate(ServerPlayer player) {
        super.eliminate(player);

        removePotato(player);
        player.setGameMode(GameType.SPECTATOR);

        if (markedPlayer == player) markedPlayer = null;
    }

    @Override
    public void participantRemoved(ServerPlayer player) {
        super.participantRemoved(player);

        if (markedPlayer != player) return;

        removePotato(player);
        markedPlayer = null;

        if (task != null) {
            task.cancel();
        }

        if (markTask != null) {
            markTask.cancel();
        }
    }

    @Override
    public void onGameOver() {
        dynamicBossBar.getBossBar().setProgress(1f);

        if (markedPlayer != null) {
            removePotato(markedPlayer);
            markedPlayer = null;
        }
    }

    private void spawnParticles() {
        if (markedPlayer == null || markedPlayer.hasDisconnected()) return;

        double x = markedPlayer.getX(), y = markedPlayer.getY(), z = markedPlayer.getZ();

        ServerLevel world = getWorld();

        world.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0, 0.2, 0);
        world.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.2, 0.2, 0.2, 0.1);
    }

    private void spawnFirework(FireworkExplosion explosion, int delay) {
        if (markedPlayer == null || markedPlayer.hasDisconnected()) return;

        double x = markedPlayer.getX(), y = markedPlayer.getY(), z = markedPlayer.getZ();

        ServerLevel world = getWorld();

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));

        FireworkRocketEntity firework = new FireworkRocketEntity(world, x, y + 3, z, rocket);
        world.addFreshEntity(firework);


        if (delay > 0) {
            gameHandle.getRootScheduler().timeout(() -> FireworkEntityAccess.explode(firework), delay);
        } else {
            FireworkEntityAccess.explode(firework);
        }
    }

    private boolean markRandomPlayer() {
        var randomPlayer = gameHandle.getParticipants().getRandomParticipant(random);

        if (randomPlayer.isEmpty()) return false;

        markPlayer(randomPlayer.get());

        return true;
    }

    private void markPlayer(ServerPlayer player) {
        if (markedPlayer != null) {
            removePotato(markedPlayer);
        }

        markedPlayer = player;

        addPotato(player);
    }

    private void addPotato(ServerPlayer player) {
        Translations translations = gameHandle.getTranslations();

        ItemStack stack = new ItemStack(Items.BAKED_POTATO);

        stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.hot_potato.item")
                .styled(style -> style.withColor(0xff0000).withItalic(false)));

        player.getInventory().setItem(4, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 4);

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.RED_WOOL));

        player.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION_SECONDS * 20, 1, false, false, false));
        glow(player, DURATION_SECONDS * 20);

        var title = translations.translateText(player, "game.ap2.hot_potato.title")
                .styled(style -> style.withColor(0xff0000).withBold(true));

        var subtitle = translations.translateText(player, "game.ap2.hot_potato.subtitle")
                .formatted(ChatFormatting.RED);

        Title.get(player).title(title, subtitle, 2, 10, 2);

        gameHandle.getScoreboardManager().joinTeam(player, team);
    }

    private void removePotato(ServerPlayer player) {
        player.getInventory().clearContent();
        player.removeAllEffects();
        Title.get(player).clear();

        gameHandle.getScoreboardManager().leaveTeam(player, team);
    }

    private void tryPassPotato(ServerPlayer player, ServerPlayer target) {
        if (player != markedPlayer) return;

        Participants participants = gameHandle.getParticipants();

        if (!participants.isParticipating(target)) return;

        markPlayer(target);
    }
}
