package work.lclpnet.ap2.impl.scene.animation;

public class Animation implements Animatable {

    private final Animatable child;
    private boolean active = false;

    public Animation(Animatable child) {
        this.child = child;
    }

    public void start() {
        active = true;
    }

    public void stop() {
        active = false;
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        if (!active) return;

        child.updateAnimation(dt, ctx);
    }

    public Animation running() {
        start();
        return this;
    }
}
