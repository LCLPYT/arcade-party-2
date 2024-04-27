package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.FallingBlockAccess;

public class SbDestruction {

    private static final int MAX_ENTITIES = 300;
    private final ServerWorld world;

    public SbDestruction(ServerWorld world) {
        this.world = world;
    }

    public void destroyIsland(SbIsland island) {
        Vec3d center = island.getCenter();

        Explosion explosion = new Explosion(world, null, null, null,
                center.getX(), center.getY(), center.getZ(), 20, false,
                Explosion.DestructionType.KEEP, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.ENTITY_GENERIC_EXPLODE);

        explosion.collectBlocksAndDamageEntities();

        int flags = Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS;
        Vec3d from = new Vec3d(0, 64, 0);
        Vec3d velocity = center.add(0, 4, 0).subtract(from).normalize();

        int entities = 0;

        for (BlockPos pos : explosion.getAffectedBlocks()) {
            if (!island.isWithinBounds(pos)) continue;

            BlockState state = world.getBlockState(pos);

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);

            if (entities >= MAX_ENTITIES) continue;

            entities++;

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
}
