package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class PillarBattleInstance extends EliminationGameInstance implements MapBootstrap {

    public static final int RANDOM_ITEM_DELAY_TICKS = 70;
    private static final int BUILD_HEIGHT = 25;
    private static final int BUILD_OUTER_RADIUS = 10;
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private @Nullable PbSetup.PlacementResult pillars = null;

    public PillarBattleInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        useSurvivalMode();
        useOldCombat();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        var setup = new PbSetup(world, map, gameHandle.getLogger());

        return setup.load().thenRun(() -> pillars = setup.placePillars(gameHandle.getParticipants(), random));
    }

    @Override
    protected void prepare() {
        useRemainingPlayersDisplay();
        useSmoothDeath();

        commons().gameRuleBuilder()
                .set(GameRules.FALL_DAMAGE, true)
                .set(GameRules.FALL_DAMAGE, true)
                .set(GameRules.DO_FIRE_TICK, true)
                .set(GameRules.DO_INSOMNIA, false)
                .set(GameRules.NATURAL_REGENERATION, true)
                .set(GameRules.DO_MOB_GRIEFING, true)
                .set(GameRules.DO_TRADER_SPAWNING, false)
                .set(GameRules.DO_PATROL_SPAWNING, false);

        movementBlocker.init(gameHandle.getHookRegistrar());

        if (pillars == null) return;

        var spawns = pillars.spawns();
        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            var spawn = spawns.get(player.getUuid());

            if (spawn == null) {
                gameHandle.getLogger().error("Failed to find spawn for {}", player.getNameForScoreboard());
                continue;
            }

            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());

            movementBlocker.disableMovement(player);
        }
    }

    @Override
    protected void ready() {
        Translations translations = gameHandle.getTranslations();

        gameHandle.protect(config -> {
            config.allowAll();

            config.disallow((entity, block) -> {
                if (entity instanceof ServerPlayerEntity player && outOfBounds(block)) {
                    var msg = translations.translateText(player, "game.ap2.pillar_battle.out_of_bounds").formatted(Formatting.RED);
                    player.sendMessage(msg, true);
                    player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0f, 0.5f);
                    return true;
                }

                return false;
            }, ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.PLACE_FLUID);
        });

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }

        commons().whenBelowCriticalHeight().then(player -> player.damage(player.getDamageSources().outOfWorld(), player.getHealth()));

        var randomizer = new PbRandomizer(random, gameHandle.getParticipants(), getWorld().getRegistryManager());

        gameHandle.getGameScheduler().interval(randomizer::giveRandomItems, RANDOM_ITEM_DELAY_TICKS);

        var hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && player.getHungerManager().getFoodLevel() >= 20) {
                player.getHungerManager().setExhaustion(12);
                player.getHungerManager().setSaturationLevel(0);
            }

            return true;
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
}
