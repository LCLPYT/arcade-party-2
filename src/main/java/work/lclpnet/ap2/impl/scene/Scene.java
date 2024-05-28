package work.lclpnet.ap2.impl.scene;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.Collection;

public class Scene {

    private final ServerWorld world;
    private final Collection<Object3d> objects = new ArrayList<>();
    private TaskHandle animationTask = null;
    private volatile AnimationContext animationContext = null;

    public Scene(ServerWorld world) {
        this.world = world;
    }

    public void add(Object3d object) {
        objects.add(object);
    }

    public void spawnObjects() {
        for (Object3d object : objects) {
            object.updateMatrixWorld();

            for (Object3d obj : object.traverse()) {
                if (obj instanceof Spawnable spawnable) {
                    spawnable.spawn(world);
                }
            }
        }
    }

    public void animate(int tickRate, TaskScheduler scheduler) {
        if (tickRate < 1) {
            throw new IllegalArgumentException("Tick rate must be at least 1");
        }

        if (animationContext == null) {
            synchronized (this) {
                if (animationContext == null) {
                    animationContext = new AnimationContext(world);
                }
            }
        }

        double dt = tickRate / 20d;
        animationTask = scheduler.interval(() -> updateAnimation(dt), tickRate);
    }

    public void stopAnimation() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }


    private void updateAnimation(double dt) {
        for (Object3d object : objects) {
            Object3d rootChanged = null;

            for (Object3d obj : object.traverse()) {
                if (!(obj instanceof Animatable animatable)) continue;

                animatable.updateAnimation(dt, animationContext);

                if (rootChanged == null) {
                    rootChanged = obj;
                }
            }

            if (rootChanged != null) {
                rootChanged.updateMatrixWorld();
            }
        }
    }
}
