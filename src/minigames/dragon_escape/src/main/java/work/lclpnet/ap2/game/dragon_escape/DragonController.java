package work.lclpnet.ap2.game.dragon_escape;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.core.type.ApEnderDragon;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class DragonController {

    private static final double
            MIN_SPEED_BPS = 1.6,
            MAX_SPEED_BPS = 16.0,
            ACCELERATION_BPS2 = 3.0d,
            ACCELERATION_DISTANCE = 30;

    private final SplinePath path;
    private final ServerLevel world;
    private final Random random;
    private final Predicate<BlockPos> canDestroy;
    private final Iterable<ServerPlayer> remainingPlayers;

    private EnderDragon dragon = null;
    @Getter
    private double dragonProgress = 0;
    private double speedBps = 0;

    public DragonController(SplinePath path, ServerLevel world, Random random, Predicate<BlockPos> canDestroy,
                            Iterable<ServerPlayer> remainingPlayers) {
        this.path = path;
        this.world = world;
        this.random = random;
        this.canDestroy = canDestroy;
        this.remainingPlayers = remainingPlayers;
    }

    public void spawnDragon() {
        EnderDragon dragon = new EnderDragon(EntityType.ENDER_DRAGON, world);
        ((ApEnderDragon) dragon).ap2$setManuallyManaged();

        setProgress(dragon, 0);

        world.addFreshEntity(dragon);

        this.dragon = dragon;
    }

    public void init(TaskScheduler scheduler) {
        scheduler.interval(this::tick, 1);
    }

    public void startMoving(TaskScheduler scheduler) {
        scheduler.interval(this::tickMovement, 1);
    }

    private void tick() {
        if (dragon == null) return;

        destroyBlocks(BlockBox.of(dragon.getBoundingBox().inflate(0, 2, 0).move(0, -2, 0)));
    }

    private void tickMovement() {
        if (dragon == null) return;

        // acceleration / deceleration logic
        double dist = getDistanceToLastPlayer();
        double brakeDist = (speedBps * speedBps - MIN_SPEED_BPS * MIN_SPEED_BPS) / (2 * ACCELERATION_BPS2);

        if (dist > ACCELERATION_DISTANCE + brakeDist) {
            // accelerate
            speedBps = min(speedBps + ACCELERATION_BPS2 / 20.d, MAX_SPEED_BPS);
        } else {
            // decelerate
            speedBps = max(speedBps - ACCELERATION_BPS2 / 20.d, MIN_SPEED_BPS);
        }

        // convert speed in block per second to path percentage per tick
        double stepPerTick = speedBps / 20.d / path.getLength();

        dragonProgress = max(0, min(1, dragonProgress + stepPerTick));

        setProgress(dragon, dragonProgress);
    }

    /**
     * @return The distance along the path from the dragon towards the last player, in blocks.
     */
    private double getDistanceToLastPlayer() {
        final double origin = dragonProgress;
        double minDist = Double.POSITIVE_INFINITY;

        for (ServerPlayer player : remainingPlayers) {
            double progress = path.getProgress(player.position());
            double dist = progress - origin;

            if (dist >= 0 && dist < minDist) {
                minDist = dist;
            }
        }

        if (Double.isInfinite(minDist)) {
            // no player in front of the dragon
            return 0;
        }

        return minDist * path.getLength();
    }

    private void setProgress(EnderDragon dragon, double s) {
        Vec3 pos = path.samplePosition(s);

        dragon.setPos(pos);

        Vec3 dir = path.sampleDirection(s).normalize().scale(-1);

        dragon.setYRot(MathUtil.yaw(dir));
        dragon.setXRot(MathUtil.pitch(dir));
    }

    public Optional<EnderDragon> dragon() {
        return Optional.ofNullable(dragon);
    }

    private void destroyBlocks(BlockBox box) {
        int destroyed = 0;

        for (BlockPos pos : box) {
            if (world.getBlockState(pos).isAir() || !canDestroy.test(pos)) continue;

            if (world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                destroyed++;
            }
        }

        if (destroyed <= 0) return;

        var pos = new BlockPos.MutableBlockPos();

        int amount = max(1, destroyed / 20);

        for (int i = 0; i < amount; i++) {
            box.randomBlockPos(pos, random);

            world.levelEvent(LevelEvent.PARTICLES_DRAGON_BLOCK_BREAK, pos, 0);
        }
    }
}
