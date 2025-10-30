package work.lclpnet.ap2.game.paintball.util;

import com.jme3.math.Vector3f;
import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
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
import static net.minecraft.util.Formatting.RED;
import static work.lclpnet.ap2.impl.util.math.MathUtil.applySpread;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.gaco.core.util.ThreadUtil.executeOn;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class PaintGunManager {

    public static final double HIT_PAINT_RADIUS = 1.9;

    private final ServerWorld world;
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

    public PaintGunManager(ServerWorld world, Scene scene, PaintManager paintManager, PaintballTeams teams,
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

        hooks.registerHook(ElementCollisionEvents.BLOCK_COLLISION, (element, terrainObject, manifoldId) -> {
            if (element instanceof PaintballBullet bullet) {
                onBulletHitTerrain(bullet);
            }
        });

        hooks.registerHook(ElementCollisionEvents.ELEMENT_COLLISION, (first, second, manifoldId) -> {
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
        if (!(entity instanceof ServerPlayerEntity player) || !participants.isParticipating(player)) return;

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
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);

            if (owner == null || teams.getTeamManager().areTeamMates(owner, player)) return;

            var bulletSettings = bullet.getSettings();

            // bypass damage cooldown
            player.hurtTime = 0;
            player.timeUntilRegen = 0;
            player.damage(world, player.getDamageSources().create(DamageTypes.ARROW, owner, owner), bulletSettings.damage());

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
        ServerPlayerEntity owner = participants.getParticipant(bullet.getOwner()).orElse(null);

        if (owner == null) return;

        PaintballTeam team = teams.teamOf(owner).orElse(null);

        if (team == null) return;

        DyeTeamKey key = team.key();

        var settings = bullet.getSettings();

        int playerDeficit = teams.playerDeficit(team);
        radius *= 1f + (playerDeficit * settings.deficitPaintBoost());

        Box box = Box.of(new Vec3d(x, y, z), radius * 2, radius * 2, radius * 2);

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

        world.spawnParticles(new DustParticleEffect(teamKey.color(), 0.5f), x, y, z, 10,
                0.2, 0.2, 0.2, 0.1);

        return true;
    }

    public void shoot(ServerPlayerEntity player, PaintGun paintGun, ItemStack stack) {
        if (!shootingEnabled || player.getItemCooldownManager().isCoolingDown(stack) || isReloading(player)) return;

        if (stack.getDamage() >= stack.getMaxDamage()) {
            translations.translateText("game.ap2.paintball.no_ink").formatted(RED).sendTo(player, true);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.2f, 2f);
            return;
        }

        BlockState state = getPaintBulletState(player).orElse(null);

        if (state == null) return;

        player.getItemCooldownManager().set(stack, paintGun.cooldownTicks());

        stack.set(DataComponentTypes.DAMAGE, stack.getDamage() + 1);

        for (int i = 0; i < paintGun.bulletCount(); i++) {
            spawnPaintBulletWithSpread(player, paintGun, state);
        }

        var fireSound = paintGun.fireSound();

        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                fireSound.sound(), SoundCategory.PLAYERS, fireSound.volume(), fireSound.pitch());

        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 2,
                0.3, 0.3, 0.3, 0.2);
    }

    public @NotNull Optional<BlockState> getPaintBulletState(ServerPlayerEntity player) {
        return teams.teamOf(player)
                .map(PaintballTeam::key)
                .map(paintManager::getPaintBulletState);
    }

    public void spawnPaintBulletWithSpread(ServerPlayerEntity player, PaintGun paintGun, BlockState state) {
        PaintGun.BulletSettings bulletSettings = paintGun.bullet();
        final double scale = bulletSettings.size();

        Vec3d dir = applySpread(player.getRotationVector(), toRadians(paintGun.bulletSpread()), random);

        Vec3d pos = getProjectileSpawn(player, dir, scale);

        executeOn(PhysicsThread.get(world), () -> spawnPaintBullet(player, state, bulletSettings, pos, dir));
    }

    public void spawnPaintBullet(ServerPlayerEntity player, BlockState state, PaintGun.BulletSettings bulletSettings, Vec3d pos, Vec3d dir) {
        var obj = new PaintballBullet(scene, state, player.getEntityWorld(), bulletSettings, this, debugController);
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(bulletSettings.size());
        obj.setOwner(player.getUuid());

        SceneRigidBody rigidBody = obj.getRigidBody();

        obj.updateRigidBody(rigidBody);

        Vec3d velocity = getProjectileVelocity(dir, bulletSettings);

        rigidBody.setLinearVelocity(toBullet(velocity));
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));
        rigidBody.setCollisionGroup(teams.bulletGroup(player));
        rigidBody.setCollideWithGroups(teams.bulletCollisionFlags(player));

        scene.add(obj);
    }

    public Vec3d getProjectileSpawn(ServerPlayerEntity player, Vec3d dir, double projectileSize) {
        final double spawnDist = 1.4;

        HitResult hit = RayCastUtil.raycast(
                player.getEntityWorld(), player.getEyePos(), dir, spawnDist,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, ShapeContext.absent(),
                entity -> !entity.isSpectator());

        Vec3d pos = hit.getPos();

        if (hit instanceof BlockHitResult blockHit) {
            pos = pos.add(blockHit.getSide().getDoubleVector().multiply(0.5 * projectileSize));
        } else if (hit.getType() != HitResult.Type.MISS) {
            pos = pos.add(dir.multiply(-0.5 * projectileSize));
        }

        return pos;
    }

    private Vec3d getProjectileVelocity(Vec3d dir, PaintGun.BulletSettings bulletSettings) {
        final double basePower = bulletSettings.power();
        final double minPowerScale = 0.65;
        final double maxPowerScale = 1.0;

        double verticalComponent = max(0, dir.y);
        double powerScale = maxPowerScale + (minPowerScale - maxPowerScale) * verticalComponent;

        return dir.multiply(basePower * powerScale);
    }

    public Optional<Pair<PaintGun, ItemStack>> getPaintGunAndStack(ServerPlayerEntity player) {
        KitManager kitManager = this.kitManager;

        if (kitManager == null) return Optional.empty();

        for (ItemStack stack : player.getInventory()) {
            if (!(SingleItemKit.get(stack, kitManager).orElse(null) instanceof PaintGunKit kit)) continue;

            return Optional.of(Pair.of(kit.getPaintGun(), stack));
        }

        return Optional.empty();
    }

    public void setReloading(ServerPlayerEntity player) {
        reloading.add(player.getUuid());
    }

    public void removeReloading(ServerPlayerEntity player) {
        reloading.remove(player.getUuid());
    }

    public boolean isReloading(ServerPlayerEntity player) {
        return reloading.contains(player.getUuid());
    }

    public void refillPaintGun(ItemStack stack) {
        stack.set(DataComponentTypes.DAMAGE, 0);
    }

    public void refillPaintGun(ServerPlayerEntity player) {
        getPaintGunAndStack(player)
                .ifPresent(pair -> refillPaintGun(pair.right()));
    }
}
