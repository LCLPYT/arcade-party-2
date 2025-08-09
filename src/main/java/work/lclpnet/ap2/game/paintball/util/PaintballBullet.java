package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.UUID;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public class PaintballBullet extends PaintballProjectile {

    private static final double
            FADE_TIME_SECONDS = 1.5d,
            MAX_TRAVEL_DIST = 256;

    private static final int MAX_AGE = Ticks.seconds(8);

    private static final boolean DEBUG_SPLITTING = false;

    @Getter
    private final PaintGun.BulletSettings settings;
    private final PaintGunManager paintManager;
    private final DebugController debugController;
    @Getter @Setter
    private UUID owner = null;
    private final Vector3d initialScale = new Vector3d();
    private final Vector3d startPos = new Vector3d();
    private double fadeTime = -1;
    private double despawnTimer = -1;
    @Getter @Setter
    private boolean painting = true;
    private int hits = 0;
    private double splitTimer = 0;
    private int splits = 0;
    @Getter @Setter
    private boolean playerContact = false;

    public PaintballBullet(Scene scene, BlockState blockState, ServerWorld world, PaintGun.BulletSettings settings,
                           PaintGunManager paintManager, DebugController debugController) {
        super(scene, blockState, world);
        this.settings = settings;
        this.paintManager = paintManager;
        this.debugController = debugController;

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
    public void updateAnimation(double dt, AnimationContext ctx) {
        super.updateAnimation(dt, ctx);

        if (getAgeTicks() >= MAX_AGE) {
            detach();
            return;
        }

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
        if (splits >= settings.split().maxSplits() || isFading() || !painting) return;

        if (settings.split().splitTicks() == Integer.MAX_VALUE) return;

        if (abs(splitTimer) <= 1e-6) {
            splitTimer = settings.split().splitTicks() / 20f;
            splits++;

            split();
        } else {
            splitTimer = max(0, splitTimer - dt);
        }
    }

    private void split() {
        Vector3f loc = getRigidBody().getFrame().getLocation(new Vector3f(), 1);
        Vec3d start = new Vec3d(loc.x, loc.y, loc.z);
        Vec3d dir = Direction.DOWN.getDoubleVector();

        if (DEBUG_SPLITTING) {
            debugController.renderer().ifPresent(renderer -> {
                renderer.marker(start, Blocks.DIAMOND_BLOCK.getDefaultState(), 0x5555ff);
                renderer.arrow(start, dir,  Blocks.BLACK_CONCRETE.getDefaultState());
            });
        }

        BlockHitResult hit = RayCastUtil.raycastBlocks(world, start, dir, 10, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent());

        if (hit.getType() != HitResult.Type.BLOCK) return;

        Vec3d pos = hit.getPos();

        paintManager.paintAt(this, pos.x, pos.y, pos.z, settings.split().splitPaintRadius(), false);
    }

    public void startDespawnTimer() {
        if (despawnTimer >= 0 || isFading()) return;

        despawnTimer = settings.despawnSeconds();
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
        if (++hits == settings.maxHits()) {
            startFading();
        }
    }
}
