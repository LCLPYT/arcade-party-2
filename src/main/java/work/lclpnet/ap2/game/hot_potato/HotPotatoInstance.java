package work.lclpnet.ap2.game.hot_potato;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.GameOverListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;

import java.util.List;
import java.util.Random;

public class HotPotatoInstance extends EliminationGameInstance implements GameOverListener {

    public static final int DURATION_SECONDS = 20;
    private final Random random = new Random();
    private DynamicTranslatedBossBar dynamicBossBar;
    private ServerPlayerEntity markedPlayer = null;
    private Team team;
    private TaskHandle task = null;

    public HotPotatoInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        winManager.addListener(this);

        dynamicBossBar = useRemainingPlayersDisplay();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        team = scoreboardManager.createTeam("team");
        team.setColor(Formatting.DARK_RED);
    }

    @Override
    protected void ready() {
        nextRound();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.ATTACK_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && entity instanceof ServerPlayerEntity hitPlayer) {
                tryPassPotato(serverPlayer, hitPlayer);
            }

            return ActionResult.PASS;
        });

        hooks.registerHook(PlayerInteractionHooks.USE_ENTITY, (player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && entity instanceof ServerPlayerEntity hitPlayer) {
                tryPassPotato(serverPlayer, hitPlayer);
            }

            return ActionResult.PASS;
        });
    }

    private void nextRound() {
        if (!markRandomPlayer()) {
            winManager.winNobody();
            return;
        }

        TranslatedBossBar bossBar = dynamicBossBar.getBossBar();

        task = gameHandle.getGameScheduler().interval(new SchedulerAction() {
            int t = 0, i = DURATION_SECONDS;

            @Override
            public void run(RunningTask info) {
                t++;

                spawnParticles();

                if (t < 20) return;

                t = 0;
                i--;

                bossBar.setPercent(MathHelper.clamp((float) i / DURATION_SECONDS, 0f, 1f));

                if (i > 0) {
                    spawnFirework(new FireworkExplosionComponent(FireworkExplosionComponent.Type.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false), 1);
                    return;
                }

                spawnFirework(new FireworkExplosionComponent(FireworkExplosionComponent.Type.LARGE_BALL, IntList.of(0xff0000), IntList.of(0xfff200), false, true), 0);

                info.cancel();

                eliminate(markedPlayer);

                bossBar.setPercent(1f);
            }
        }, 1).whenComplete(this::onRoundOver);
    }

    private void onRoundOver() {
        if (winManager.isGameOver()) return;

        gameHandle.getGameScheduler().timeout(this::nextRound, Ticks.seconds(3));
    }

    @Override
    protected void eliminate(ServerPlayerEntity player) {
        super.eliminate(player);

        removePotato(player);
        player.changeGameMode(GameMode.SPECTATOR);

        if (markedPlayer == player) markedPlayer = null;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        super.participantRemoved(player);

        if (markedPlayer == player) {
            removePotato(player);
            markedPlayer = null;

            if (task != null) {
                task.cancel();
            }
        }
    }

    @Override
    public void onGameOver() {
        dynamicBossBar.getBossBar().setPercent(1f);

        if (markedPlayer != null) {
            removePotato(markedPlayer);
            markedPlayer = null;
        }
    }

    private void spawnParticles() {
        if (markedPlayer == null || markedPlayer.isDisconnected()) return;

        double x = markedPlayer.getX(), y = markedPlayer.getY(), z = markedPlayer.getZ();

        ServerWorld world = getWorld();

        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0, 0.2, 0);
        world.spawnParticles(ParticleTypes.FLAME, x, y, z, 2, 0.2, 0.2, 0.2, 0.1);
    }

    private void spawnFirework(FireworkExplosionComponent explosion, int delay) {
        if (markedPlayer == null || markedPlayer.isDisconnected()) return;

        double x = markedPlayer.getX(), y = markedPlayer.getY(), z = markedPlayer.getZ();

        ServerWorld world = getWorld();

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(explosion)));

        FireworkRocketEntity firework = new FireworkRocketEntity(world, x, y + 3, z, rocket);
        world.spawnEntity(firework);


        if (delay > 0) {
            gameHandle.getScheduler().timeout(() -> FireworkEntityAccess.explode(firework), delay);
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

    private void markPlayer(ServerPlayerEntity player) {
        if (markedPlayer != null) {
            removePotato(markedPlayer);
        }

        markedPlayer = player;

        addPotato(player);
    }

    private void addPotato(ServerPlayerEntity player) {
        TranslationService translations = gameHandle.getTranslations();

        ItemStack stack = new ItemStack(Items.BAKED_POTATO);

        stack.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.hot_potato.item")
                .styled(style -> style.withColor(0xff0000).withItalic(false)));

        player.getInventory().setStack(4, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 4);

        player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.RED_WOOL));

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DURATION_SECONDS * 20, 1, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, DURATION_SECONDS * 20, 1, false, false, false));

        var title = translations.translateText(player, "game.ap2.hot_potato.title")
                .styled(style -> style.withColor(0xff0000).withBold(true));

        var subtitle = translations.translateText(player, "game.ap2.hot_potato.subtitle")
                .formatted(Formatting.RED);

        Title.get(player).title(title, subtitle, 2, 10, 2);

        gameHandle.getScoreboardManager().joinTeam(player, team);
    }

    private void removePotato(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.clearStatusEffects();
        Title.get(player).clear();

        gameHandle.getScoreboardManager().leaveTeam(player, team);
    }

    private void tryPassPotato(ServerPlayerEntity player, ServerPlayerEntity target) {
        if (player != markedPlayer) return;

        Participants participants = gameHandle.getParticipants();

        if (!participants.isParticipating(target)) return;

        markPlayer(target);
    }
}
