package work.lclpnet.ap2.impl.scene;

import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class Scene {

    private final MountContext mountContext;
    private final Collection<Object3d> objects = new ArrayList<>();
    private final Collection<Object3d> toAdd = new ArrayList<>();
    private final Collection<Object3d> toRemove = new ArrayList<>();
    private TaskHandle animationTask = null;
    private volatile AnimationContext animationContext = null;
    private boolean iterating = false;

    public Scene(MountContext mountContext) {
        this.mountContext = mountContext;
    }

    public void add(Object3d object) {
        if (iterating) {
            toAdd.add(object);
            return;
        }

        if (!objects.add(object)) return;

        object.updateMatrixWorld();

        for (Object3d obj : object.traverse()) {
            if (obj instanceof Mountable mountable) {
                mountable.mount(mountContext);
            }
        }
    }

    public void remove(Object3d object) {
        if (iterating) {
            toRemove.add(object);
            return;
        }

        if (!objects.remove(object)) return;

        for (Object3d obj : object.traverse()) {
            if (obj instanceof Unmountable mountable) {
                mountable.unmount(mountContext);
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
                    animationContext = new AnimationContext(mountContext.world());
                }
            }
        }

        for (Object3d object : iterate()) {
            for (Object3d obj : object.traverse()) {
                if (obj instanceof Interpolatable interpolatable) {
                    interpolatable.updateTickRate(tickRate);
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

    public void clear() {
        for (Object3d obj : iterate()) {
            remove(obj);
        }
    }

    private void updateAnimation(double dt) {
        for (Object3d object : iterate()) {
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

    public Iterable<Object3d> iterate() {
        return () -> {
            iterating = true;

            var parent = objects.iterator();

            return new Iterator<>() {
                boolean done = false;

                @Override
                public boolean hasNext() {
                    boolean hasNext = parent.hasNext();

                    if (!hasNext && !done) {
                        done = true;
                        iterating = false;
                        toAdd.forEach(Scene.this::add);
                        toRemove.forEach(Scene.this::remove);
                        toAdd.clear();
                        toRemove.clear();
                    }

                    return hasNext;
                }

                @Override
                public Object3d next() {
                    return parent.next();
                }
            };
        };
    }
}
