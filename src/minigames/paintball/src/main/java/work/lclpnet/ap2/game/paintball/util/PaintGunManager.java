package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.game.paintball.kit.PaintGunKit;
import work.lclpnet.ap2.impl.game.kit.KitManager;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.physics.EntityRefPhysicsElement;
import work.lclpnet.gaco.scene.physics.SceneRigidBody;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.physics.api.PhysicsElement;
import work.lclpnet.kibu.physics.api.event.collision.ElementCollisionEvents;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.function.BooleanSupplier;

import static java.lang.Math.max;
import static java.lang.Math.toRadians;
import static net.minecraft.ChatFormatting.RED;
import static work.lclpnet.ap2.impl.util.math.MathUtil.applySpread;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.gaco.core.util.ThreadUtil.executeOn;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class PaintGunManager {

    public static final double HIT_PAINT_RADIUS = 1.9;

    private final ServerLevel world;
    private final Scene scene;
    @Getter
    private final PaintManager paintManager;
    private final PaintballTeams teams;
    private final Random random;
    private final Participants participants;
    private final Translations translations;
    private final DebugController debugController;
    private final BooleanSupplier gameOver;
    private final Set<UUID> reloading = new HashSet<>();
    @Setter
    private boolean shootingEnabled = false;
    private @Nullable KitManager kitManager = null;

    public PaintGunManager(ServerLevel world, Scene scene, PaintManager paintManager, PaintballTeams teams,
                           Random random, Participants participants, Translations translations,
                           DebugController debugController, BooleanSupplier gameOver) {
        this.world = world;
        this.scene = scene;
        this.paintManager = paintManager;
        this.teams = teams;
        this.random = random;
        this.participants = participants;
        this.translations = translations;
        this.debugController = debugController;
        this.gameOver = gameOver;
    }

    public void injectKitManager(KitManager kitManager) {
        this.kitManager = kitManager;
    }

    public void init(HookRegistrar hooks) {
        MinecraftSpace.get(world).setCollisionEventsEnabled(true);

        ElementCollisionEvents.BLOCK_COLLISION.registerWith(hooks, (element, _, _) -> {
            if (element instanceof PaintballBullet bullet) {
                onBulletHitTerrain(bullet);
            }
        });

        ElementCollisionEvents.ELEMENT_COLLISION.registerWith(hooks, (first, second, _) -> {
            if (first instanceof PaintballBullet bullet && bulletCollision(bullet, second)) return;
            if (second instanceof PaintballBullet bullet && bulletCollision(bullet, first)) return;

            // prevent ink bullets kicked by a player to kick other ink bullets that paint very far away blocks
            if (first instanceof PaintballBullet bulletA && second instanceof PaintballBullet bulletB
                    && (bulletA.isPlayerContact() || bulletB.isPlayerContact())) {
                bulletA.setPlayerContact(true);
                bulletB.setPlayerContact(true);
                bulletA.setPainting(false);
                bulletB.setPainting(false);
            }
        });
    }

    private boolean bulletCollision(PaintballBullet bullet, PhysicsElement<?> other) {
        if (bullet.getAgeTicks() >= PaintballProjectile.TEAM_COLLISION_ENABLE_TICKS) {
            // prevent indefinite ink stacking on top of other ink bullet
            bullet.startDespawnTimer();
        }

        if (other instanceof EntityRefPhysicsElement entityElem) {
            entityElem.cast().optional().ifPresent(entity -> onBulletHitEntity(bullet, entity));
            return true;
        }

        return false;
    }

    private void onBulletHitEntity(PaintballBullet bullet, Entity entity) {
        if (!(entity instanceof ServerPlayer player) || !participants.isParticipating(player)) return;

        bullet.setPlayerContact(true);
        bullet.setPainting(false);

        if (bullet.isFading()) return;

        bullet.startFading();
        bullet.forcePhysicsThread();

        Vector3f velocity = bullet.getRigidBody().getLinearVelocity(new Vector3f());

        if (velocity.lengthSquared() < 0.2) return;

        limitVelocity(bullet);

        UUID ownerUuid = bullet.getOwner();

        if (ownerUuid == null) return;

        world.getServer().execute(() -> {
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerUuid);

            if (owner == null || teams.getTeamManager().areTeamMates(owner, player)) return;

            var bulletSettings = bullet.getSettings();

            // bypass damage cooldown
            player.hurtTime = 0;
            player.invulnerableTime = 0;
            player.hurtServer(world, player.damageSources().source(DamageTypes.ARROW, owner, owner), bulletSettings.damage());

            paintAt(bullet, player.getX(), player.getY(), player.getZ(), HIT_PAINT_RADIUS, true);
        });
    }

    private void onBulletHitTerrain(PaintballBullet bullet) {
        bullet.startDespawnTimer();

        limitVelocity(bullet);

        if (gameOver.getAsBoolean() || !bullet.isPainting()) return;

        bullet.onHit();

        Vector3f hit = bullet.getRigidBody().getFrame().getLocation(new Vector3f(), 1);

        executeOn(world.getServer(), () -> paintAt(bullet, hit.x, hit.y, hit.z, bullet.getSettings().paintRadius(), true));
    }

    public void paintAt(PaintballBullet bullet, double x, double y, double z, double radius, boolean shouldCount) {
        ServerPlayer owner = participants.getParticipant(bullet.getOwner()).orElse(null);

        if (owner == null) return;

        PaintballTeam team = teams.teamOf(owner).orElse(null);

        if (team == null) return;

        DyeTeamKey key = team.key();

        var settings = bullet.getSettings();

        int playerDeficit = teams.playerDeficit(team);
        radius *= 1f + (playerDeficit * settings.deficitPaintBoost());

        AABB box = AABB.ofSize(new Vec3(x, y, z), radius * 2, radius * 2, radius * 2);

        for (BlockPos pos : BlockBox.of(box)) {
            double dx = pos.getX() + 0.5 - x;
            double dy = pos.getY() + 0.5 - y;
            double dz = pos.getZ() + 0.5 - z;

            if (dx * dx + dy * dy + dz * dz <= radius * radius && tryPaint(key, pos, x, y, z) && shouldCount) {
                bullet.onHit();
            }
        }
    }

    public void limitVelocity(PaintballBullet bullet) {
        bullet.forcePhysicsThread();

        // limit velocity so that ink bullets don't bounce extremely far
        SceneRigidBody rigidBody = bullet.getRigidBody();
        var velocity = new Vector3f();
        rigidBody.getLinearVelocity(velocity);

        final float maxPower = bullet.getSettings().maxImpactPower();

        if (velocity.lengthSquared() > maxPower * maxPower) {
            rigidBody.setLinearVelocity(velocity.normalize().mult(maxPower));
        }
    }

    private boolean tryPaint(DyeTeamKey teamKey, BlockPos blockPos, double x, double y, double z) {
        if (!paintManager.replace(blockPos, teamKey)) return false;

        world.sendParticles(new DustParticleOptions(teamKey.color(), 0.5f), x, y, z, 10,
                0.2, 0.2, 0.2, 0.1);

        return true;
    }

    public void shoot(ServerPlayer player, PaintGun paintGun, ItemStack stack) {
        if (!shootingEnabled || player.getCooldowns().isOnCooldown(stack) || isReloading(player)) return;

        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            translations.translateText("game.ap2.paintball.no_ink").formatted(RED).sendTo(player, true);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.2f, 2f);
            return;
        }

        BlockState state = getPaintBulletState(player).orElse(null);

        if (state == null) return;

        player.getCooldowns().addCooldown(stack, paintGun.cooldownTicks());

        stack.set(DataComponents.DAMAGE, stack.getDamageValue() + 1);

        for (int i = 0; i < paintGun.bulletCount(); i++) {
            spawnPaintBulletWithSpread(player, paintGun, state);
        }

        var fireSound = paintGun.fireSound();

        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                fireSound.sound(), SoundSource.PLAYERS, fireSound.volume(), fireSound.pitch());

        world.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 2,
                0.3, 0.3, 0.3, 0.2);
    }

    public @NotNull Optional<BlockState> getPaintBulletState(ServerPlayer player) {
        return teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState);
    }

    public void spawnPaintBulletWithSpread(ServerPlayer player, PaintGun paintGun, BlockState state) {
        PaintGun.BulletSettings bulletSettings = paintGun.bullet();
        final double scale = bulletSettings.size();

        Vec3 dir = applySpread(player.getLookAngle(), toRadians(paintGun.bulletSpread()), random);

        Vec3 pos = getProjectileSpawn(player, dir, scale);

        executeOn(PhysicsThread.get(world), () -> spawnPaintBullet(player, state, bulletSettings, pos, dir));
    }

    public void spawnPaintBullet(ServerPlayer player, BlockState state, PaintGun.BulletSettings bulletSettings, Vec3 pos, Vec3 dir) {
        var obj = new PaintballBullet(scene, state, player.level(), bulletSettings, this, debugController);
        obj.position.set(pos.x(), pos.y(), pos.z());
        obj.scale.set(bulletSettings.size());
        obj.setOwner(player.getUUID());

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        Vec3 velocity = getProjectileVelocity(dir, bulletSettings);

        rigidBody.setLinearVelocity(toBullet(velocity));
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));
        rigidBody.setCollisionGroup(teams.bulletGroup(player));
        rigidBody.setCollideWithGroups(teams.bulletCollisionFlags(player));

        scene.add(obj);
    }

    public Vec3 getProjectileSpawn(ServerPlayer player, Vec3 dir, double projectileSize) {
        final double spawnDist = 1.4;

        HitResult hit = RayCastUtil.raycast(
                player.level(), player.getEyePosition(), dir, spawnDist,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, CollisionContext.empty(),
                entity -> !entity.isSpectator());

        Vec3 pos = hit.getLocation();

        if (hit instanceof BlockHitResult blockHit) {
            pos = pos.add(blockHit.getDirection().getUnitVec3().scale(0.5 * projectileSize));
        } else if (hit.getType() != HitResult.Type.MISS) {
            pos = pos.add(dir.scale(-0.5 * projectileSize));
        }

        return pos;
    }

    private Vec3 getProjectileVelocity(Vec3 dir, PaintGun.BulletSettings bulletSettings) {
        final double basePower = bulletSettings.power();
        final double minPowerScale = 0.65;
        final double maxPowerScale = 1.0;

        double verticalComponent = max(0, dir.y);
        double powerScale = maxPowerScale + (minPowerScale - maxPowerScale) * verticalComponent;

        return dir.scale(basePower * powerScale);
    }

    public Optional<Pair<PaintGun, ItemStack>> getPaintGunAndStack(ServerPlayer player) {
        KitManager kitManager = this.kitManager;

        if (kitManager == null) return Optional.empty();

        for (ItemStack stack : player.getInventory()) {
            if (!(SingleItemKit.get(stack, kitManager).orElse(null) instanceof PaintGunKit kit)) continue;

            return Optional.of(Pair.of(kit.getPaintGun(), stack));
        }

        return Optional.empty();
    }

    public void setReloading(ServerPlayer player) {
        reloading.add(player.getUUID());
    }

    public void removeReloading(ServerPlayer player) {
        reloading.remove(player.getUUID());
    }

    public boolean isReloading(ServerPlayer player) {
        return reloading.contains(player.getUUID());
    }

    public void refillPaintGun(ItemStack stack) {
        stack.set(DataComponents.DAMAGE, 0);
    }

    public void refillPaintGun(ServerPlayer player) {
        getPaintGunAndStack(player)
                .ifPresent(pair -> refillPaintGun(pair.right()));
    }
}
