package work.lclpnet.ap2.game.dragon_escape;

import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;
import work.lclpnet.ap2.core.type.ApEnderDragon;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import static java.lang.Math.*;

public class DragonController {

    private static final double BASE_SPEED_BPS = 2.5d;

    private final SplinePath path;
    private final ServerWorld world;
    private final Random random;
    private final Predicate<BlockPos> canDestroy;
    private final double baseStepPerTick;

    private EnderDragonEntity dragon = null;
    @Getter
    private double dragonProgress = 0;

    public DragonController(SplinePath path, ServerWorld world, Random random, Predicate<BlockPos> canDestroy) {
        this.path = path;
        this.world = world;
        this.random = random;
        this.canDestroy = canDestroy;

        double length = path.getLength();

        baseStepPerTick = BASE_SPEED_BPS / 20.d / length;
    }

    public void spawnDragon() {
        EnderDragonEntity dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
        ((ApEnderDragon) dragon).ap2$setManuallyManaged();

        setProgress(dragon, 0);

        world.spawnEntity(dragon);

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

        destroyBlocks(BlockBox.of(dragon.getBoundingBox().expand(0, 2, 0).offset(0, -2, 0)));
    }

    private void tickMovement() {
        dragonProgress = max(0, min(1, dragonProgress + baseStepPerTick));

        if (dragon == null) return;

        setProgress(dragon, dragonProgress);
    }

    private void setProgress(EnderDragonEntity dragon, double s) {
        Vec3d pos = path.samplePosition(s);

        dragon.setPosition(pos);

        Vec3d dir = path.sampleDirection(s).normalize().multiply(-1);

        dragon.setYaw(MathUtil.yaw(dir));
        dragon.setPitch(MathUtil.pitch(dir));
    }

    public Optional<EnderDragonEntity> dragon() {
        return Optional.ofNullable(dragon);
    }

    private void destroyBlocks(BlockBox box) {
        int destroyed = 0;

        for (BlockPos pos : box) {
            if (world.getBlockState(pos).isAir() || !canDestroy.test(pos)) continue;

            if (world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) {
                destroyed++;
            }
        }

        if (destroyed <= 0) return;

        var pos = new BlockPos.Mutable();

        int amount = max(1, destroyed / 20);

        for (int i = 0; i < amount; i++) {
            box.randomBlockPos(pos, random);

            world.syncWorldEvent(WorldEvents.ENDER_DRAGON_BREAKS_BLOCK, pos, 0);
        }
    }
}
