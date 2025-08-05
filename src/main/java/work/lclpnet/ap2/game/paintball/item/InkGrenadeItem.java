package work.lclpnet.ap2.game.paintball.item;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.AdvancedExplosionBehavior;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback;
import work.lclpnet.ap2.core.mixin.ExplosionImplAccessor;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.world.ExplosionUtil;
import work.lclpnet.kibu.hook.HookRegistrar;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.SoundHelper.playSound;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toMinecraft;

public class InkGrenadeItem implements SpecialItem {

    private static final float SIZE = 0.3f, THROW_POWER = 16f;

    private final PaintGunManager paintGunManager;
    private final Scene scene;
    private final Random random;
    private final PaintballTeams teams;
    private final PaintGun.BulletSettings bulletSettings;

    public InkGrenadeItem(PaintGunManager paintGunManager, Scene scene, Random random, PaintballTeams teams) {
        this.paintGunManager = paintGunManager;
        this.scene = scene;
        this.random = random;
        this.teams = teams;
        this.bulletSettings = new PaintGun.BulletSettings(
                0.08, 16, 2, 2, 0.01f, 0.5f, 2f,
                1.6f, 0f, PaintGun.BulletSplit.NO_SPLIT
        );
    }

    @Override
    public String id() {
        return "ink_grenade";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.TNT);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(DeathMessageItemCallback.HOOK, (source, killed, stack) -> ItemStack.EMPTY);
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        if (hand == Hand.OFF_HAND) return ActionResult.PASS;

        throwInkGrenade(player, stack);

        return ActionResult.SUCCESS_SERVER;
    }

    @Override
    public void onSwing(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        throwInkGrenade(player, stack);
    }

    private void throwInkGrenade(ServerPlayerEntity player, ItemStack stack) {
        ServerWorld world = player.getWorld();

        spawnObject(player);

        playSound(world, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 0.8f, 1.2f);

        stack.decrementUnlessCreative(1, player);
    }

    private void spawnObject(ServerPlayerEntity player) {
        Vec3d dir = player.getRotationVector();
        Vec3d pos = paintGunManager.getProjectileSpawn(player, dir, SIZE);

        var obj = new InkGrenadeObject(player.getWorld());
        obj.position.set(pos.getX(), pos.getY(), pos.getZ());
        obj.scale.set(SIZE);
        obj.setThrower(player.getUuid());

        SceneRigidBody rigidBody = obj.getRigidBody();

        rigidBody.setLinearVelocity(toBullet(dir.multiply(THROW_POWER)));
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)));
        rigidBody.setPhysicsLocation(toBullet(pos));
        rigidBody.setCollisionGroup(teams.bulletGroup(player));
        rigidBody.setCollideWithGroups(teams.bulletCollisionFlags(player));

        obj.updateRigidBody(rigidBody);

        scene.add(obj);
    }

    private class InkGrenadeObject extends PaintballProjectile {

        private static final float
                BLINK_SECONDS = 0.5f,
                FUSE_SECONDS = 4.0f,
                MASS = 0.1f,
                FRAGMENT_SPAWN_RADIUS = 0.2f,
                EXPLOSION_POWER = 3.8f;

        private static final int EXPLOSION_FRAGMENTS = 50;

        @Getter @Setter
        private UUID thrower = null;
        private double blinkTimer = 0;
        private double fuseTimer = FUSE_SECONDS;
        private boolean flash = false;

        public InkGrenadeObject(ServerWorld world) {
            super(Blocks.TNT.getDefaultState(), world);
            rigidBody.setMass(MASS);
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            super.updateAnimation(dt, ctx);

            blinkTimer += dt;
            fuseTimer -= dt;

            if (blinkTimer >= BLINK_SECONDS) {
                blinkTimer -= BLINK_SECONDS;

                BlockState newState = flash ? Blocks.TNT.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
                flash = !flash;

                setBlockState(newState);
                rigidBody.setMass(MASS);
            }

            if (fuseTimer > 0) return;

            explode();
        }

        private void explode() {
            this.detach();

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(thrower);

            if (player == null) return;

            var state = paintGunManager.getPaintBulletState(player).orElse(null);

            if (state == null) return;

            PaintballTeam team = teams.teamOf(player).orElse(null);

            if (team == null) return;

            Vec3d pos = new Vec3d(toMinecraft(getRigidBody().getPhysicsLocation(null)));

            createExplosion(player, pos, team);

            spawnFragments(pos, player, state);
        }

        private void createExplosion(ServerPlayerEntity player, Vec3d pos, PaintballTeam team) {
            var behavior = new AdvancedExplosionBehavior(true, true, Optional.empty(), Optional.empty());

            var explosion = new ExplosionImpl(world, player, null, behavior,
                    pos, EXPLOSION_POWER, false,
                    Explosion.DestructionType.KEEP);

            // mimic behaviour of ServerWorld::createExplosion
            world.emitGameEvent(null, GameEvent.EXPLODE, pos);
            world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 1.0, 0.0, 0.0, 1);

            var access = (ExplosionImplAccessor) explosion;

            for (BlockPos affectedPos : access.invokeGetBlocksToDestroy()) {
                paintGunManager.getPaintManager().replace(affectedPos, team.key());
            }

            // calculate damage and knockback
            access.invokeDamageEntities();

            ExplosionUtil.sendExplosion(world, explosion);
        }

        private void spawnFragments(Vec3d pos, ServerPlayerEntity player, BlockState state) {
            for (var offset : MathUtil.fibonacciHemisphere(EXPLOSION_FRAGMENTS)) {
                Vec3d dir = new Vec3d(offset.x, offset.y, offset.z);
                Vec3d fragPos = pos.add(dir.multiply(FRAGMENT_SPAWN_RADIUS));

                paintGunManager.spawnPaintBullet(player, state, bulletSettings, fragPos, dir);
            }
        }
    }
}
