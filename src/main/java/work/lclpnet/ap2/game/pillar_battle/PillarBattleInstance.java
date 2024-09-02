package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.lobby.game.impl.prot.MutableProtectionConfig;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PillarBattleInstance extends EliminationGameInstance implements MapBootstrap {

    public static final int RANDOM_ITEM_DELAY_TICKS = 70;
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private Map<UUID, PositionRotation> spawns = null;

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

        return setup.load().thenRun(() -> spawns = setup.placePillars(gameHandle.getParticipants(), random));
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
                .set(GameRules.NATURAL_REGENERATION, false)
                .set(GameRules.DO_MOB_GRIEFING, true)
                .set(GameRules.DO_TRADER_SPAWNING, false)
                .set(GameRules.DO_PATROL_SPAWNING, false);

        movementBlocker.init(gameHandle.getHookRegistrar());

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
        gameHandle.protect(MutableProtectionConfig::allowAll);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }

        commons().whenBelowCriticalHeight().then(player -> player.damage(player.getDamageSources().outOfWorld(), player.getHealth()));

        var randomizer = new PbRandomizer(random, gameHandle.getParticipants(), getWorld().getRegistryManager());

        gameHandle.getGameScheduler().interval(randomizer::giveRandomItems, RANDOM_ITEM_DELAY_TICKS);
    }
}
