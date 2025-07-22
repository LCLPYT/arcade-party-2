package work.lclpnet.ap2.game.paintball.util;

import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.math.Vector3f;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.physics.api.event.collision.ElementCollisionEvents;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;

import java.util.Random;
import java.util.function.BooleanSupplier;

import static java.lang.Math.max;
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
                onBulletHitTerrain(bullet, manifoldId);
            }
        });
    }

    private void onBulletHitTerrain(PaintballBullet bullet, long manifoldId) {
        bullet.startDespawnTimer();
        bullet.onHit();

        if (gameOver.getAsBoolean() || !bullet.isPainting()) return;

        ServerPlayerEntity owner = participants.getParticipant(bullet.getOwner()).orElse(null);

        if (owner == null) return;

        PaintballTeam team = teams.teamOf(owner).orElse(null);

        if (team == null) return;

        DyeTeamKey key = team.key();
        final float d = 0.75f;
        final boolean bulletIsObjA = PersistentManifolds.getBodyAId(manifoldId) == bullet.getRigidBody().nativeId();

        for (long pointId : PersistentManifolds.listPointIds(manifoldId)) {
            Vector3f pos = new Vector3f();
            Vector3f normal = new Vector3f();

            if (bulletIsObjA) ManifoldPoints.getPositionWorldOnB(pointId, pos);
            else ManifoldPoints.getPositionWorldOnA(pointId, pos);

            ManifoldPoints.getNormalWorldOnB(pointId, normal);

            pos = pos.subtract(normal.mult(0.1f));

            // hit pos
            tryPaint(bullet, key, pos, 0, 0, 0);

            // cardinal directions
            tryPaint(bullet, key, pos,  d,  0,  0);
            tryPaint(bullet, key, pos,  0,  d,  0);
            tryPaint(bullet, key, pos,  0,  0,  d);
            tryPaint(bullet, key, pos, -d,  0,  0);
            tryPaint(bullet, key, pos,  0, -d,  0);
            tryPaint(bullet, key, pos,  0,  0, -d);

            // diagonal
            tryPaint(bullet, key, pos,  d,  d,  d);
            tryPaint(bullet, key, pos,  d,  d, -d);
            tryPaint(bullet, key, pos,  d, -d,  d);
            tryPaint(bullet, key, pos,  d, -d, -d);
            tryPaint(bullet, key, pos, -d,  d,  d);
            tryPaint(bullet, key, pos, -d,  d, -d);
            tryPaint(bullet, key, pos, -d, -d,  d);
            tryPaint(bullet, key, pos, -d, -d, -d);
        }
    }

    private void tryPaint(PaintballBullet bullet, DyeTeamKey teamKey, Vector3f pos, float dx, float dy, float dz) {
        var blockPos = BlockPos.ofFloored(pos.x + dx, pos.y + dy, pos.z + dz);

        if (!paintManager.replace(blockPos, teamKey)) return;

        world.spawnParticles(new DustParticleEffect(teamKey.color(), 0.5f), pos.x, pos.y, pos.z, 10,
                0.2, 0.2, 0.2, 0.1);

        if (dx * dx + dy * dy + dz * dz > 0) {
            bullet.onHit();
        }
    }

    public void shoot(ServerPlayerEntity player) {
        if (!shootingEnabled) return;

        BlockState state = teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState)
                .orElse(null);

        if (state == null) return;

        final double scale = 0.2;

        Vec3d pos = getProjectileSpawn(player, scale);

        var obj = new PaintballBullet(state, player.getWorld());
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(scale);
        obj.setOwner(player.getUuid());

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        Vec3d velocity = getProjectileVelocity(player);

        rigidBody.setLinearVelocity(toBullet(velocity));
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));

        scene.add(obj);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 2f);

        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 2,
                0.3, 0.3, 0.3, 0.2);
    }

    private Vec3d getProjectileSpawn(ServerPlayerEntity player, double scale) {
        final double spawnDist = 1.4;

        HitResult hit = RayCastUtil.raycast(
                player.getWorld(), player.getEyePos(), player.getRotationVector(), spawnDist,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, ShapeContext.absent(),
                entity -> !entity.isSpectator());

        Vec3d pos = hit.getPos();

        if (hit instanceof BlockHitResult blockHit) {
            pos = pos.add(blockHit.getSide().getDoubleVector().multiply(0.5 * scale));
        } else if (hit.getType() != HitResult.Type.MISS) {
            pos = pos.add(player.getRotationVector().multiply(-0.5 * scale));
        }

        return pos;
    }

    private Vec3d getProjectileVelocity(ServerPlayerEntity player) {
        final double basePower = 16;
        final double minPowerScale = 0.65;
        final double maxPowerScale = 1.0;

        Vec3d dir = player.getRotationVector();

        double verticalComponent = max(0, dir.y);
        double powerScale = maxPowerScale + (minPowerScale - maxPowerScale) * verticalComponent;

        return dir.multiply(basePower * powerScale);
    }
}
