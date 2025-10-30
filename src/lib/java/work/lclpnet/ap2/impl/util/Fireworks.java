package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.List;
import java.util.Random;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static net.minecraft.util.math.MathHelper.floor;

public class Fireworks {

    private static final double
            TRAIL_CHANCE = 0.75,
            TWINKLE_CHANCE = 0.35;

    private static final int
            MIN_COLORS = 1,
            MAX_COLORS = 3;

    private final ServerWorld world;
    private final Vec3d basePosition;
    private final double radius;
    private final Random random;

    public Fireworks(ServerWorld world, Vec3d basePosition, double radius, Random random) {
        this.world = world;
        this.basePosition = basePosition;
        this.radius = radius;
        this.random = random;
    }

    public Action<Runnable> start(TaskScheduler scheduler, int durationTicks, int minDelayTicks, int maxDelayTicks) {
        Hook<Runnable> onDone = HookFactory.createArrayBacked(Runnable.class, hooks -> () -> {
            for (Runnable hook : hooks) {
                hook.run();
            }
        });

        scheduler.interval(new SchedulerAction() {
            int t = 0;
            int nextFirework = randomDelay();

            private int randomDelay() {
                return minDelayTicks + random.nextInt(maxDelayTicks - minDelayTicks + 1);
            }

            @Override
            public void run(RunningTask task) {
                if (nextFirework-- <= 0) {
                    nextFirework = randomDelay();

                    spawnFirework();
                }

                if (++t >= durationTicks) {
                    task.cancel();
                }
            }
        }, 1).whenComplete(() -> onDone.invoker().run());

        return onDone::register;
    }

    public void spawnFirework() {
        double angle = random.nextDouble() * Math.PI * 2;
        double x = basePosition.x + cos(angle) * radius;
        double z = basePosition.z + sin(angle) * radius;

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, floor(x), floor(z));

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, getRandomExplosions()));

        FireworkRocketEntity firework = new FireworkRocketEntity(world, x, topY + 3, z, rocket);
        world.spawnEntity(firework);
    }

    private @NotNull List<FireworkExplosionComponent> getRandomExplosions() {
        var types = FireworkExplosionComponent.Type.values();
        var type = types[random.nextInt(types.length)];

        IntList colors = randomColors();
        IntList fadeColors = randomColors();

        boolean trail = random.nextDouble() <= TRAIL_CHANCE;
        boolean twinkle = random.nextDouble() <= TWINKLE_CHANCE;

        var explosion = new FireworkExplosionComponent(type, colors, fadeColors, trail, twinkle);
        return List.of(explosion);
    }

    private IntList randomColors() {
        int amount = MIN_COLORS + random.nextInt(MAX_COLORS - MIN_COLORS + 1);
        int[] colors = new int[amount];

        for (int i = 0; i < amount; i++) {
            colors[i] = ColorUtil.getRandomHsvColor(random);
        }

        return IntList.of(colors);
    }

    public static void spawnGoalFirework(ServerPlayerEntity player) {
        var explosion = new FireworkExplosionComponent(FireworkExplosionComponent.Type.LARGE_BALL, IntList.of(0x20FF4D), IntList.of(0x1E7220), false, true);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(explosion)));

        ServerWorld world = player.getEntityWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, player.getX(), player.getY(), player.getZ(), rocket);
        world.spawnEntity(firework);

        FireworkEntityAccess.explode(firework);
    }
}
