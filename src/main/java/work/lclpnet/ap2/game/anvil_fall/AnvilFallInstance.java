package work.lclpnet.ap2.game.anvil_fall;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;

public class AnvilFallInstance extends EliminationGameInstance {

    private final Direction[] directions = new Direction[] {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.WEST};
    private final Random random = new Random();
    private AnvilFallSetup setup;
    private TaskHandle spawnerTask;

    public AnvilFallInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.DO_ENTITY_DROPS).set(false, null);
        gameRules.get(GameRules.FALL_DAMAGE).set(true, null);

        scanWorld();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> {
            if (damageSource.isOf(DamageTypes.FALLING_ANVIL) && entity instanceof ServerPlayerEntity serverPlayer) {
                onHitByAnvil(serverPlayer);
            }

            return false;
        }));

        startAnvilSpawning();
    }

    @Override
    protected void onGameOver() {
        super.onGameOver();

        if (spawnerTask != null) {
            spawnerTask.cancel();
        }
    }

    private void onHitByAnvil(ServerPlayerEntity player) {
        if (!gameHandle.getParticipants().isParticipating(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        world.playSound(null, x, y, z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1f, 0.7f);
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 25, 0.1, 0.1, 0.1, 0f);

        eliminate(player);
    }

    private void scanWorld() {
        ServerWorld world = getWorld();
        BlockBox box = MapUtil.readBox(getMap().requireProperty("anvil-box"));

        setup = AnvilFallSetup.scanWorld(world, box, random);
    }

    private void startAnvilSpawning() {
        spawnerTask = gameHandle.getScheduler().interval(new Runnable() {
            int anvilDelay = 4;
            int nextAnvil = anvilDelay;
            int timer = 0;

            @Override
            public void run() {
                int time = ++timer;

                if (time % 140 == 0) {
                    anvilDelay--;
                }

                if (nextAnvil > 0) {
                    nextAnvil--;
                    return;
                }

                nextAnvil = anvilDelay;

                final int count = anvilDelay >= 0 ? 1 : Math.min(Math.abs(anvilDelay), 256);

                for (int i = 0; i < count; i++) {
                    spawnRandomAnvil();
                }
            }
        }, 1);
    }

    private void spawnRandomAnvil() {
        ServerWorld world = getWorld();
        BlockPos pos = setup.getRandomPosition();

        BlockState state = Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, directions[random.nextInt(directions.length)]);

        FallingBlockEntity anvil = new FallingBlockEntity(EntityType.FALLING_BLOCK, world);
        anvil.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        anvil.setSilent(true);
        anvil.timeFalling = 1;
        anvil.setHurtEntities(2.0f, 40);
        FallingBlockAccess.setDropItem(anvil, false);
        FallingBlockAccess.setDestroyedOnLanding(anvil, true);
        FallingBlockAccess.setBlockState(anvil, state);

        world.spawnEntity(anvil);
    }
}
