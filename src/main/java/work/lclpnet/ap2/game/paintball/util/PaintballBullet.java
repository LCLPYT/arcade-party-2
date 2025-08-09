package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.Random;
import java.util.UUID;

import static java.lang.Math.max;
import static java.lang.Math.toRadians;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class PaintballBullet extends PaintballProjectile {

    private static final double
            FADE_TIME_SECONDS = 1.5d,
            MAX_TRAVEL_DIST = 256,
            MIN_SPLIT_POWER = 10;

    @Getter
    private final PaintGun.BulletSettings settings;
    private final Scene scene;
    private final Random random;
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

    public PaintballBullet(Scene scene, BlockState blockState, ServerWorld world, PaintGun.BulletSettings settings, Random random) {
        super(scene, blockState, world);
        this.settings = settings;
        this.scene = scene;
        this.random = random;

        rigidBody.setMass(settings.mass());

        // prevent tunneling at high velocities
        if (settings.power() >= 20) {
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
    public synchronized void updateAnimation(double dt, AnimationContext ctx) {
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

    private synchronized void tickSplitting(double dt) {
        if (splitOff || splits >= settings.split().maxSplits() || isFading()) return;

        if (settings.split().splitTicks() == Integer.MAX_VALUE) return;

//        var velocity = new Vector3f();
//        rigidBody.getLinearVelocity(velocity);
//
//        if (velocity.lengthSquared() < MIN_SPLIT_POWER * MIN_SPLIT_POWER) return;

        if (splitTimer <= 0) {
            splitTimer = settings.split().splitTicks() / 20f;
            splits++;

            executePhysics(this::split);
        } else {
            splitTimer = max(0, splitTimer - dt);
        }
    }

    public synchronized void setSplitOff() {
        splitOff = true;
    }

    private void split() {
        var obj = new PaintballBullet(scene, getBlockState(), world, settings, random);
        obj.position.set(position);
        obj.scale.set(settings.size() * 0.2f);
        obj.setSplitOff();
        obj.setOwner(owner);

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        var parentVelocity = new Vector3f();
        this.rigidBody.getLinearVelocity(parentVelocity);
        parentVelocity.normalize();

        double horizontal = 0.2;
        Vec3d dir = new Vec3d(parentVelocity.x * horizontal, -1, parentVelocity.z * horizontal).normalize();
        dir = MathUtil.applySpread(dir, toRadians(settings.split().splitSpread()), random);

        rigidBody.setLinearVelocity(toBullet(dir.multiply(5)));
        rigidBody.setPhysicsLocation(new Vector3f((float) position.x, (float) position.y, (float) position.z));

        int collisionGroup = this.rigidBody.getCollisionGroup();
        rigidBody.setCollisionGroup(collisionGroup >> 1);
        rigidBody.setCollideWithGroups(this.rigidBody.getCollideWithGroups() & ~collisionGroup);

        scene.add(obj);
    }

    public synchronized void startDespawnTimer() {
        if (despawnTimer >= 0 || isFading()) return;

        despawnTimer = settings.despawnSeconds();
    }

    public synchronized boolean isFading() {
        return fadeTime >= 0;
    }

    public synchronized void startFading() {
        if (isFading()) return;

        fadeTime = FADE_TIME_SECONDS;
        initialScale.set(scale);
        painting = false;
    }

    public synchronized void onHit() {
        if (++hits == settings.maxHits() || splitOff) {
            startFading();
        }
    }
}
