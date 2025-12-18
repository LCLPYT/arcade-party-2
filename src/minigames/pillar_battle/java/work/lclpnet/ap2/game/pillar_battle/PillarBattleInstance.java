package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.core.type.ApDragonFight;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.world.WorldBorderUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PillarBattleInstance extends EliminationGameInstance implements MapBootstrap {

    public static final int RANDOM_ITEM_DELAY_TICKS = 70;
    private static final int BUILD_HEIGHT = 25;
    private static final int BUILD_OUTER_RADIUS = 10;
    private static final int BORDER_WARN_DISTANCE = 2;
    private static final int BORDER_WARN_DELAY_MS = 2000;
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private @Nullable PbSetup.PlacementResult pillars = null;
    private final Map<UUID, Warning> warnings = new HashMap<>();
    private @Nullable WorldBorder border = null;

    public PillarBattleInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        useSurvivalMode();
        useOldCombat();
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        var setup = new PbSetup(world, map, gameHandle.getLogger());

        return setup.load().thenRun(() -> pillars = setup.placePillars(gameHandle.getParticipants(), random));
    }

    @Override
    protected void prepare() {
        useRemainingPlayersDisplay();
        useSmoothDeath();

        commons().gameRuleBuilder()
                .set(GameRules.RULE_FALL_DAMAGE, true)
                .set(GameRules.RULE_FALL_DAMAGE, true)
                .set(GameRules.RULE_DOFIRETICK, true)
                .set(GameRules.RULE_DOINSOMNIA, false)
                .set(GameRules.RULE_NATURAL_REGENERATION, true)
                .set(GameRules.RULE_MOBGRIEFING, true)
                .set(GameRules.RULE_DO_TRADER_SPAWNING, false)
                .set(GameRules.RULE_DO_PATROL_SPAWNING, false)
                .set(GameRules.RULE_KEEPINVENTORY, false);

        movementBlocker.init(gameHandle.getHooks());

        if (pillars == null) return;

        var spawns = pillars.spawns();
        ServerLevel world = getWorld();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            var spawn = spawns.get(player.getUUID());

            if (spawn == null) {
                gameHandle.getLogger().error("Failed to find spawn for {}", player.getScoreboardName());
                continue;
            }

            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);

            movementBlocker.disableMovement(player);
        }

        setupWorldBorder();
    }

    private void setupWorldBorder() {
        if (pillars == null) return;

        BlockPos center = pillars.center();
        double radius = pillars.radius() + BUILD_OUTER_RADIUS + 0.5;

        border = WorldBorderUtil.createBorder(center.getX() + 0.5, center.getZ() + 0.5, radius * 2);
        border.setWarningBlocks(0);
    }

    @Override
    protected void go() {
        Translations translations = gameHandle.getTranslations();

        gameHandle.protect(config -> {
            config.allowAll();

            config.disallow((entity, block) -> {
                if (entity instanceof ServerPlayer player && outOfBounds(block)) {
                    var msg = translations.translateText(player, "game.ap2.pillar_battle.out_of_bounds").formatted(ChatFormatting.RED);
                    player.displayClientMessage(msg, true);
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0f, 0.5f);
                    return true;
                }

                return false;
            }, ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.PLACE_FLUID);
        });

        for (ServerPlayer player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }

        commons().whenBelowCriticalHeight().then(player -> player.hurtServer(player.level(), player.damageSources().fellOutOfWorld(), player.getHealth()));

        var randomizer = new PbRandomizer(random, gameHandle.getParticipants(), getWorld().registryAccess());

        var scheduler = gameHandle.getScheduler();
        scheduler.interval(randomizer::giveRandomItems, RANDOM_ITEM_DELAY_TICKS);

        var hooks = gameHandle.getHooks();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && player.getFoodData().getFoodLevel() >= 20) {
                player.getFoodData().addExhaustion(12);
                player.getFoodData().setSaturation(0);
            }

            return true;
        });

        handleEnderDragonAi(hooks);

        scheduler.interval(this::warnWorldBorder, 1);
    }

    private void handleEnderDragonAi(HookRegistrar hooks) {
        if (pillars == null) return;

        BlockPos center = pillars.center();

        hooks.registerHook(ServerEntityHooks.ENTITY_LOAD, (entity, world) -> {
            if (!(entity instanceof EnderDragon dragon)) return;

            var data = new EndDragonFight.Data(false, false, false, false,
                    Optional.of(dragon.getUUID()), Optional.of(center), Optional.of(List.of()));

            EndDragonFight fight = new EndDragonFight(world, random.nextLong(), data, center);
            ((ApDragonFight) fight).ap2$setTemporary();

            dragon.setDragonFight(fight);
            dragon.setFightOrigin(center);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        });
    }

    private boolean outOfBounds(BlockPos pos) {
        if (pillars == null) return true;

        BlockPos center = pillars.center();
        int maxY = center.getY() + BUILD_HEIGHT;

        if (pos.getY() > maxY) return true;

        int cx = center.getX(), cz = center.getZ();
        int totalRadius = pillars.radius() + BUILD_OUTER_RADIUS;
        int minX = cx - totalRadius, maxX = cx + totalRadius;
        int minZ = cz - totalRadius, maxZ = cz + totalRadius;
        int x = pos.getX(), z = pos.getZ();

        return x < minX || x > maxX || z < minZ || z > maxZ;
    }

    private void warnWorldBorder() {
        if (pillars == null) return;

        Translations translations = gameHandle.getTranslations();
        BlockPos center = pillars.center();
        double cx = center.getX(), cz = center.getZ();
        double totalRadius = pillars.radius() + BUILD_OUTER_RADIUS + 0.5;

        WorldBorder realBorder = getWorld().getWorldBorder();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            UUID uuid = player.getUUID();
            Warning warning = warnings.computeIfAbsent(uuid, u -> new Warning());

            double dx = totalRadius - Math.abs(cx + 0.5 - player.getX());
            double dz = totalRadius - Math.abs(cz + 0.5 - player.getZ());

            if (dx > BORDER_WARN_DISTANCE && dz > BORDER_WARN_DISTANCE) {
                // not near the border
                if (warning.warned) {
                    warning.warned = false;

                    WorldBorderUtil.init(player, realBorder);

                    if (System.currentTimeMillis() - warning.lastWarning < 62 * 50) {
                        player.displayClientMessage(Component.empty(), true);
                    }
                }
                continue;
            }

            // near the border
            if (warning.warned) continue;

            warning.warned = true;

            if (border != null) {
                // send fake world border
                WorldBorderUtil.init(player, border);
            }

            long timestamp = System.currentTimeMillis();

            if (timestamp - warning.lastWarning < BORDER_WARN_DELAY_MS) continue;

            warning.lastWarning = timestamp;

            var msg = translations.translateText(player, "game.ap2.pillar_battle.border_warn")
                    .styled(style -> style.withColor(0xff0000).withBold(true));

            player.displayClientMessage(msg, true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 0.3f, 0.5f);
        }
    }

    private static class Warning {
        private boolean warned = false;
        private long lastWarning = 0;
    }
}
