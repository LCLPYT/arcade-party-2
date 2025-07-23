package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.physics.api.event.collision.ElementCollisionEvents;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static java.lang.Math.*;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class PaintGunManager {

    private final ServerWorld world;
    private final Scene scene;
    private final PaintManager paintManager;
    private final PaintballTeams teams;
    private final Random random;
    private final Participants participants;
    private final BooleanSupplier gameOver;
    @Setter
    private boolean shootingEnabled = false;

    public PaintGunManager(ServerWorld world, Scene scene, PaintManager paintManager, PaintballTeams teams,
                           Random random, Participants participants, BooleanSupplier gameOver) {
        this.world = world;
        this.scene = scene;
        this.paintManager = paintManager;
        this.teams = teams;
        this.random = random;
        this.participants = participants;
        this.gameOver = gameOver;
    }

    public void init(HookRegistrar hooks) {
        MinecraftSpace.get(world).setCollisionEventsEnabled(true);

        hooks.registerHook(ElementCollisionEvents.BLOCK_COLLISION, (element, terrainObject, manifoldId) -> {
            if (element instanceof PaintballBullet bullet) {
                onBulletHitTerrain(bullet);
            }
        });
    }

    private void onBulletHitTerrain(PaintballBullet bullet) {
        bullet.startDespawnTimer();

        limitVelocity(bullet);

        if (gameOver.getAsBoolean() || !bullet.isPainting()) return;

        bullet.onHit();

        ServerPlayerEntity owner = participants.getParticipant(bullet.getOwner()).orElse(null);

        if (owner == null) return;

        PaintballTeam team = teams.teamOf(owner).orElse(null);

        if (team == null) return;

        DyeTeamKey key = team.key();

        Vector3f hit = new Vector3f();
        bullet.getRigidBody().getPhysicsLocation(hit);

        final float r = bullet.getPaintGun().bullet().paintRadius();

        Box box = Box.of(new Vec3d(hit.x, hit.y, hit.z), r * 2, r * 2, r * 2);

        for (BlockPos pos : BlockBox.of(box)) {
            double dx = pos.getX() + 0.5 - hit.x;
            double dy = pos.getY() + 0.5 - hit.y;
            double dz = pos.getZ() + 0.5 - hit.z;

            if (dx * dx + dy * dy + dz * dz <= r * r && tryPaint(key, pos, hit)) {
                bullet.onHit();
            }

        }
    }

    private void limitVelocity(PaintballBullet bullet) {
        // limit velocity so that ink bullets don't bounce extremely far
        SceneRigidBody rigidBody = bullet.getRigidBody();
        var velocity = new Vector3f();
        rigidBody.getLinearVelocity(velocity);

        final float maxPower = bullet.getPaintGun().bullet().maxImpactPower();

        if (velocity.lengthSquared() > maxPower * maxPower) {
            rigidBody.setLinearVelocity(velocity.normalize().mult(maxPower));
        }
    }

    private boolean tryPaint(DyeTeamKey teamKey, BlockPos blockPos, Vector3f pos) {
        if (!paintManager.replace(blockPos, teamKey)) return false;

        world.spawnParticles(new DustParticleEffect(teamKey.color(), 0.5f), pos.x, pos.y, pos.z, 10,
                0.2, 0.2, 0.2, 0.1);

        return true;
    }

    public void shoot(ServerPlayerEntity player, PaintGun paintGun, ItemStack stack) {
        if (!shootingEnabled || player.getItemCooldownManager().isCoolingDown(stack)) return;

        BlockState state = teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState)
                .orElse(null);

        if (state == null) return;

        player.getItemCooldownManager().set(stack, paintGun.cooldownTicks());

        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < paintGun.bulletCount(); i++) {
                spawnPaintBullet(player, paintGun, state);
            }
        }, MinecraftSpace.get(player.getWorld()).getWorkerThread());

        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 2f);

        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 2,
                0.3, 0.3, 0.3, 0.2);
    }

    private void spawnPaintBullet(ServerPlayerEntity player, PaintGun paintGun, BlockState state) {
        final double scale = paintGun.bullet().size();

        Vec3d dir = player.getRotationVector();
        dir = applySpread(dir, paintGun);

        Vec3d pos = getProjectileSpawn(player, dir, scale);

        var obj = new PaintballBullet(state, player.getWorld(), paintGun);
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(scale);
        obj.setOwner(player.getUuid());

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        Vec3d velocity = getProjectileVelocity(dir, paintGun);

        rigidBody.setLinearVelocity(toBullet(velocity));
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));

        scene.add(obj);
    }

    private Vec3d getProjectileSpawn(ServerPlayerEntity player, Vec3d dir, double scale) {
        final double spawnDist = 1.4;

        HitResult hit = RayCastUtil.raycast(
                player.getWorld(), player.getEyePos(), dir, spawnDist,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, ShapeContext.absent(),
                entity -> !entity.isSpectator());

        Vec3d pos = hit.getPos();

        if (hit instanceof BlockHitResult blockHit) {
            pos = pos.add(blockHit.getSide().getDoubleVector().multiply(0.5 * scale));
        } else if (hit.getType() != HitResult.Type.MISS) {
            pos = pos.add(dir.multiply(-0.5 * scale));
        }

        return pos;
    }

    private Vec3d getProjectileVelocity(Vec3d dir, PaintGun paintGun) {
        final double basePower = paintGun.bullet().power();
        final double minPowerScale = 0.65;
        final double maxPowerScale = 1.0;

        double verticalComponent = max(0, dir.y);
        double powerScale = maxPowerScale + (minPowerScale - maxPowerScale) * verticalComponent;

        return dir.multiply(basePower * powerScale);
    }

    private Vec3d applySpread(Vec3d dir, PaintGun paintGun) {
        double cosMax = cos(toRadians(paintGun.bulletSpread()));
        double cosTheta = cosMax + (1 - cosMax) * random.nextDouble();
        double sinTheta = sqrt(1 - cosTheta * cosTheta);

        double phi = random.nextDouble() * 2 * PI;

        Vec3d t = new Vec3d(1, 0, 0);

        if (abs(dir.dotProduct(t)) > 0.999) {
            t = new Vec3d(0, 1, 0);
        }

        Vec3d axis = dir.crossProduct(t).normalize();
        Vec3d perp = dir.crossProduct(axis).normalize();

        return axis.multiply(cos(phi) * sinTheta)
                .add(perp.multiply(sin(phi) * sinTheta))
                .add(dir.multiply(cosTheta))
                .normalize();
    }
}
