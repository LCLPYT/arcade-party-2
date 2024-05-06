package work.lclpnet.ap2.game.speed_builders.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;

import java.util.Random;

public class SbDestruction {

    private static final float LAUNCHED_PERCENTAGE = 0.36f;
    private final ServerWorld world;
    private final Random random;
    private final BreezeEntity aelos;

    public SbDestruction(ServerWorld world, Random random, BreezeEntity aelos) {
        this.world = world;
        this.random = random;
        this.aelos = aelos;
    }

    public void fireProjectile(SbIsland island) {
        Vec3d center = island.getCenter();
        Vec3d chargePos = getChargePos(aelos);
        Vec3d dir = center.subtract(chargePos);

        BreezeWindChargeEntity charge = new BreezeWindChargeEntity(aelos, world);
        charge.setVelocity(dir.getX(), dir.getY(), dir.getZ(), 0.9f, 0);
        charge.setPosition(chargePos);

        world.spawnEntity(charge);

        SoundHelper.playSound(aelos.getServer(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 1.0f);
    }

    public void destroyIsland(SbIsland island, Vec3d impactPos, Vec3d velocity) {
        Explosion explosion = new Explosion(world, null, null, null,
                impactPos.getX(), impactPos.getY(), impactPos.getZ(), 25, false,
                Explosion.DestructionType.KEEP, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.ENTITY_GENERIC_EXPLODE);

        explosion.collectBlocksAndDamageEntities();

        addEffects(impactPos);

        velocity = velocity.normalize();

        int flags = Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS;

        for (BlockPos pos : explosion.getAffectedBlocks()) {
            if (!island.isWithinBounds(pos)) continue;

            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);

            if (random.nextFloat() >= LAUNCHED_PERCENTAGE) continue;

            FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, world);
            fallingBlock.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            fallingBlock.timeFalling = 1;
            FallingBlockAccess.setDropItem(fallingBlock, false);
            FallingBlockAccess.setDestroyedOnLanding(fallingBlock, true);
            FallingBlockAccess.setBlockState(fallingBlock, state);

            VelocityModifier.setVelocity(fallingBlock, velocity);

            world.spawnEntity(fallingBlock);
        }
    }

    private void addEffects(Vec3d pos) {
        double x = pos.getX(), z = pos.getZ(), y = pos.getY();

        ParticleHelper.spawnForceParticle(ParticleTypes.GUST, x, y, z, 300,
                7, 7, 7, 0, PlayerLookup.world(world));

        ParticleHelper.spawnForceParticle(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 30,
                10, 10, 10, 0, PlayerLookup.world(world));

        ParticleHelper.spawnForceParticle(ParticleTypes.CLOUD, x, y, z, 200,
                1, 1, 1, 1, PlayerLookup.world(world));

        for (ServerPlayerEntity player : PlayerLookup.around(world, pos, 32)) {
            Vec3d eyePos = player.getEyePos();
            Vec3d soundPos = eyePos.add(pos.subtract(eyePos).normalize().multiply(8));

            double sx = soundPos.getX(), sy = soundPos.getY(), sz = soundPos.getZ();

            SoundHelper.playSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, sx, sy, sz, 1, 1.2f);
            SoundHelper.playSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, sx, sy, sz, 0.5f, 0.5f);
        }
    }

    public static Vec3d getChargePos(BreezeEntity aelos) {
        return new Vec3d(
                aelos.getX(),
                aelos.getY() + aelos.getHeight() * aelos.getAttributeValue(EntityAttributes.GENERIC_SCALE) * 0.5,
                aelos.getZ());
    }
}
