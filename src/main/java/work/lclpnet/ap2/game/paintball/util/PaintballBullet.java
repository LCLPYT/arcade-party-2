package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;

import java.util.UUID;

import static java.lang.Math.max;

public class PaintballBullet extends PhysicsBlockDisplayObject {

    private static final double
            FADE_TIME_SECONDS = 1.5d,
            MAX_TRAVEL_DIST = 256,
            MIN_SPLIT_POWER = 10;

    @Getter
    private final PaintGun paintGun;
    private final Scene scene;
    @Getter @Setter
    private UUID owner = null;
    private final Vector3d initialScale = new Vector3d();
    private final Vector3d startPos = new Vector3d();
    private double fadeTime = -1;
    private double despawnTimer = -1;
    @Getter
    private boolean painting = true;
    @Getter
    private boolean splitOff = false;
    private int hits = 0;
    private double splitTimer = 0;
    private int splits = 0;

    public PaintballBullet(BlockState blockState, ServerWorld world, PaintGun paintGun, Scene scene) {
        super(blockState, world);
        this.paintGun = paintGun;
        this.scene = scene;

        rigidBody.setMass(paintGun.bullet().mass());

        // prevent tunneling at high velocities
        if (paintGun.bullet().power() >= 20) {
            rigidBody.setCcdMotionThreshold(1e-4f);
            rigidBody.setCcdSweptSphereRadius(0.1f);
        }
    }

    @Override
    public void mount(MountContext ctx) {
        super.mount(ctx);

        startPos.set(position);
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

        // remove out of world
        if (position.y < world.getBottomY() - 40 || position.distanceSquared(startPos) > MAX_TRAVEL_DIST * MAX_TRAVEL_DIST) {
            detach();
        }

        tickSplitting(dt);
    }

    private void tickSplitting(double dt) {
        if (splitOff || splits >= paintGun.bullet().maxSplits()) return;

        var velocity = new Vector3f();
        rigidBody.getLinearVelocity(velocity);

        if (velocity.lengthSquared() < MIN_SPLIT_POWER * MIN_SPLIT_POWER) return;

        if (splitTimer <= 0) {
            splitTimer = paintGun.bullet().splitTicks() / 20;
            split();
        } else {
            splitTimer = max(0, splitTimer - dt);
        }
    }

    public void setSplitOff() {
        splitOff = true;
    }

    private void split() {
        splits++;

        var obj = new PaintballBullet(getBlockState(), world, paintGun, scene);
        obj.position.set(position);
        obj.scale.set(paintGun.bullet().size() * 0.2f);
        obj.setSplitOff();
        obj.setOwner(owner);

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        rigidBody.setLinearVelocity(new Vector3f(0, -5, 0));
        rigidBody.setPhysicsLocation(new Vector3f((float) position.x, (float) position.y, (float) position.z));

        int collisionGroup = this.rigidBody.getCollisionGroup();
        rigidBody.setCollisionGroup(collisionGroup >> 1);
        rigidBody.setCollideWithGroups(this.rigidBody.getCollideWithGroups() & ~collisionGroup);

        scene.add(obj);
    }

    public void startDespawnTimer() {
        if (despawnTimer >= 0 || isFading()) return;

        despawnTimer = paintGun.bullet().despawnSeconds();
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
        if (++hits == paintGun.bullet().maxHits() || splitOff) {
            startFading();
        }
    }
}
