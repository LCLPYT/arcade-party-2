package work.lclpnet.ap2.game.paintball.item;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.animation.AnimationContext;
import work.lclpnet.gaco.scene.physics.SceneRigidBody;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread;

import java.util.Random;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.SoundHelper.playSound;
import static work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d;
import static work.lclpnet.gaco.core.util.ThreadUtil.executeOn;
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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.TNT);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        DeathMessageItemCallback.HOOK.registerWith(hooks, (source, killed, stack) -> ItemStack.EMPTY);
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        if (hand == InteractionHand.OFF_HAND) return InteractionResult.PASS;

        throwInkGrenade(player, stack);

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void onSwing(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        throwInkGrenade(player, stack);
    }

    private void throwInkGrenade(ServerPlayer player, ItemStack stack) {
        ServerLevel world = player.level();

        executeOn(PhysicsThread.get(world), () -> spawnObject(player));

        playSound(world, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);

        stack.consume(1, player);
    }

    private void spawnObject(ServerPlayer player) {
        Vec3 dir = player.getLookAngle();
        Vec3 pos = paintGunManager.getProjectileSpawn(player, dir, SIZE);

        var obj = new InkGrenadeObject(scene, player.level());
        obj.position.set(pos.x(), pos.y(), pos.z());
        obj.scale.set(SIZE);
        obj.setThrower(player.getUUID());

        SceneRigidBody rigidBody = obj.getRigidBody();

        rigidBody.setLinearVelocity(toBullet(dir.scale(THROW_POWER)));
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

        public InkGrenadeObject(Scene scene, ServerLevel world) {
            super(scene, Blocks.TNT.defaultBlockState(), world);
            rigidBody.setMass(MASS);
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            super.updateAnimation(dt, ctx);

            blinkTimer += dt;
            fuseTimer -= dt;

            if (blinkTimer >= BLINK_SECONDS) {
                blinkTimer -= BLINK_SECONDS;

                BlockState newState = flash ? Blocks.TNT.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                flash = !flash;

                setBlockState(newState);
                rigidBody.setMass(MASS);
            }

            if (fuseTimer > 0) return;

            explode();
        }

        private void explode() {
            this.detach();

            ServerPlayer player = world.getServer().getPlayerList().getPlayer(thrower);

            if (player == null) return;

            var state = paintGunManager.getPaintBulletState(player).orElse(null);

            if (state == null) return;

            PaintballTeam team = teams.teamOf(player).orElse(null);

            if (team == null) return;

            Vec3 pos = new Vec3(position.x, position.y, position.z);

            paintGunManager.getPaintManager().createExplosion(player, pos, team, EXPLOSION_POWER);

            executeOn(PhysicsThread.get(world), () -> spawnFragments(pos, player, state));
        }

        private void spawnFragments(Vec3 pos, ServerPlayer player, BlockState state) {
            for (var offset : MathUtil.fibonacciHemisphere(EXPLOSION_FRAGMENTS)) {
                Vec3 dir = new Vec3(offset.x, offset.y, offset.z);
                Vec3 fragPos = pos.add(dir.scale(FRAGMENT_SPAWN_RADIUS));

                paintGunManager.spawnPaintBullet(player, state, bulletSettings, fragPos, dir);
            }
        }
    }
}
