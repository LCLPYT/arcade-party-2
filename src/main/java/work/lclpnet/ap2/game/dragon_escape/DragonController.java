package work.lclpnet.ap2.game.dragon_escape;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.core.type.ApEnderDragon;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class DragonController {

    private static final double BASE_SPEED_BPS = 2d;

    private final SplinePath path;
    private final ServerWorld world;
    private final double baseStepPerTick;

    private EnderDragonEntity dragon = null;
    private double dragonProgress = 0;

    public DragonController(SplinePath path, ServerWorld world) {
        this.path = path;
        this.world = world;

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

    public void start(TaskScheduler scheduler) {
        scheduler.interval(this::tick, 1);
    }

    private void tick() {
        dragonProgress = max(0, min(1, dragonProgress + baseStepPerTick));

        setProgress(dragon, dragonProgress);
    }

    private void setProgress(EnderDragonEntity dragon, double s) {
        Vec3d pos = path.samplePosition(s);

        dragon.setPosition(pos);

        Vec3d dir = path.sampleDirection(s).normalize();

        dragon.setYaw(MathUtil.yaw(dir));
        dragon.setPitch(MathUtil.pitch(dir));
    }
}
