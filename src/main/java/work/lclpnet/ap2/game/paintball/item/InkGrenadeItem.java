package work.lclpnet.ap2.game.paintball.item;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread;

import java.util.Random;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.SoundHelper.playSound;
import static work.lclpnet.ap2.impl.util.ThreadUtil.executeOn;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

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

        executeOn(PhysicsThread.get(world), () -> spawnObject(player));

        playSound(world, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 0.8f, 1.2f);

        stack.decrementUnlessCreative(1, player);
    }

    private void spawnObject(ServerPlayerEntity player) {
        Vec3d dir = player.getRotationVector();
        Vec3d pos = paintGunManager.getProjectileSpawn(player, dir, SIZE);

        var obj = new InkGrenadeObject(scene, player.getWorld());
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
                MASS = 0.8f,
                FRAGMENT_SPAWN_RADIUS = 0.2f,
                EXPLOSION_POWER = 4.5f;

        private static final int EXPLOSION_FRAGMENTS = 50;

        @Getter @Setter
        private UUID thrower = null;
        private double blinkTimer = 0;
        private double fuseTimer = FUSE_SECONDS;
        private boolean flash = false;

        public InkGrenadeObject(Scene scene, ServerWorld world) {
            super(scene, Blocks.TNT.getDefaultState(), world);
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

            Vec3d pos = new Vec3d(position.x, position.y, position.z);

            paintGunManager.getPaintManager().createExplosion(player, pos, team, EXPLOSION_POWER);

            executeOn(PhysicsThread.get(world), () -> spawnFragments(pos, player, state));
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
