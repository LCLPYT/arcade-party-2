package work.lclpnet.ap2.game.speed_builders.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;

import java.util.Random;
import java.util.UUID;

public class SbDestruction {

    private static final float LAUNCHED_PERCENTAGE = 0.4f;
    private final ServerLevel world;
    private final Random random;
    private final UUID aelosId;

    public SbDestruction(ServerLevel world, Random random, UUID aelosId) {
        this.world = world;
        this.random = random;
        this.aelosId = aelosId;
    }

    private Breeze aelos() {
        Entity entity = world.getEntity(aelosId);

        if (entity instanceof Breeze breeze) {
            return breeze;
        }

        throw new IllegalStateException("Aelos not found");
    }

    public void setAelosLookingTowards(SbIsland island) {
        Breeze aelos = aelos();
        Vec3 center = island.getCenter();

        aelos.lookAt(EntityAnchorArgument.Anchor.EYES, center);
    }

    public BreezeWindCharge fireProjectile(SbIsland island) {
        Breeze aelos = aelos();

        Vec3 center = island.getCenter();
        Vec3 chargePos = getChargePos(aelos);
        Vec3 dir = center.subtract(chargePos);

        BreezeWindCharge charge = new BreezeWindCharge(aelos, world);
        charge.shoot(dir.x(), dir.y(), dir.z(), 0.9f, 0);
        charge.setPos(chargePos);

        world.addFreshEntity(charge);

        SoundHelper.playSound(aelos.level().getServer(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 1.5f, 1.0f);

        return charge;
    }

    public void destroyIsland(SbIsland island, Vec3 impactPos, Vec3 velocity) {
        var explosion = new ServerExplosion(world, null, null, null,
                impactPos, 25, false,
                Explosion.BlockInteraction.KEEP);

        var access = (ServerExplosionAccessor) explosion;

        world.gameEvent(null, GameEvent.EXPLODE, impactPos);
        addEffects(impactPos);

        velocity = velocity.normalize();

        int flags = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        for (BlockPos pos : access.invokeCalculateExplodedPositions()) {
            if (!island.getBounds().contains(pos)) continue;

            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            world.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);

            if (random.nextFloat() >= LAUNCHED_PERCENTAGE) continue;

            FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, world);
            fallingBlock.setPosRaw(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            fallingBlock.time = 1;
            FallingBlockAccess.setDropItem(fallingBlock, false);
            FallingBlockAccess.setDestroyedOnLanding(fallingBlock, true);
            FallingBlockAccess.setBlockState(fallingBlock, state);

            VelocityModifier.setVelocity(fallingBlock, velocity);

            world.addFreshEntity(fallingBlock);
        }
    }

    private void addEffects(Vec3 pos) {
        double x = pos.x(), z = pos.z(), y = pos.y();

        ParticleHelper.spawnForceParticle(ParticleTypes.GUST, x, y, z, 300,
                7, 7, 7, 0, PlayerLookup.world(world));

        ParticleHelper.spawnForceParticle(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 30,
                10, 10, 10, 0, PlayerLookup.world(world));

        ParticleHelper.spawnForceParticle(ParticleTypes.CLOUD, x, y, z, 200,
                1, 1, 1, 1, PlayerLookup.world(world));

        for (ServerPlayer player : PlayerLookup.around(world, pos, 32)) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 soundPos = eyePos.add(pos.subtract(eyePos).normalize().scale(8));

            double sx = soundPos.x(), sy = soundPos.y(), sz = soundPos.z();

            SoundHelper.playSound(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, sx, sy, sz, 1, 1.2f);
            SoundHelper.playSound(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, sx, sy, sz, 0.5f, 0.5f);
        }
    }

    public static Vec3 getChargePos(Breeze aelos) {
        return new Vec3(aelos.getX(), aelos.getY(0.8), aelos.getZ());
    }
}
