package work.lclpnet.ap2.game.paintball.util;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;

import java.util.UUID;

import static java.lang.Math.max;

public class PaintballBullet extends PhysicsBlockDisplayObject {

    private static final double FADE_TIME_SECONDS = 1.5d;

    @Getter
    private final PaintGun paintGun;
    @Getter @Setter
    private UUID owner = null;
    private final Vector3d initialScale = new Vector3d();
    private double fadeTime = -1;
    private double despawnTimer = -1;
    @Getter
    private boolean painting = true;
    private int hits = 0;

    public PaintballBullet(BlockState blockState, ServerWorld world, PaintGun paintGun) {
        super(blockState, world);
        this.paintGun = paintGun;

        rigidBody.setMass(paintGun.bulletMass());
    }

    @Override
    public MinecraftShape.Convex createShape() {
        MinecraftShape.Convex shape = super.createShape();
        shape.setMargin(0.02f);

        return shape;
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        super.updateAnimation(dt, ctx);

        if (despawnTimer >= 0) {
            despawnTimer = max(despawnTimer - dt, 0d);

            if (despawnTimer <= 0) {
                despawnTimer = -1;
                startFading();
            }
        }

        if (isFading()) {
            fadeTime = max(fadeTime - dt, 0d);
            scale.set(initialScale).mul(fadeTime / FADE_TIME_SECONDS);

            if (fadeTime <= 0) {
                fadeTime = -1;
                detach();
            }
        }
    }

    public void startDespawnTimer() {
        if (despawnTimer >= 0 || isFading()) return;

        despawnTimer = paintGun.bulletDespawnSeconds();
    }

    public boolean isFading() {
        return fadeTime >= 0;
    }

    public void startFading() {
        if (isFading()) return;

        fadeTime = FADE_TIME_SECONDS;
        initialScale.set(scale);
        painting = false;
    }

    public void onHit() {
        if (++hits == paintGun.maxBulletHits()) {
            startFading();
        }
    }
}
