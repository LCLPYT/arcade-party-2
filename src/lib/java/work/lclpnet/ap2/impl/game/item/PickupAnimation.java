package work.lclpnet.ap2.impl.game.item;

import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;

import java.util.function.Consumer;

import static java.lang.Math.max;
import static java.lang.Math.min;

class PickupAnimation implements Animatable {

    private static final double DURATION_SECONDS = 0.15;
    private final Object3d object;
    private final Vector3d startPos = new Vector3d(), targetPos = new Vector3d();
    private final Consumer<Vector3d> targetUpdater;
    private final Runnable whenDone;
    private double time = 0;
    private boolean done = false;

    PickupAnimation(Object3d object, Consumer<Vector3d> targetUpdater, Runnable whenDone) {
        this.object = object;
        this.targetUpdater = targetUpdater;
        this.whenDone = whenDone;

        startPos.set(object.position);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        if (done) return;

        targetUpdater.accept(targetPos);

        time += dt;

        double t = max(0.d, min(1.d, time / DURATION_SECONDS));
        t *= t;

        startPos.lerp(targetPos, t, object.position);

        if (t >= 1.d) {
            done = true;
            whenDone.run();
        }
    }
}
