package work.lclpnet.ap2.game.speed_builders.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.speed_builders.data.SbIsland
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.access.entity.FallingBlockAccess
import java.util.*

private const val LAUNCHED_PERCENTAGE = 0.4f

fun getChargePos(aelos: Breeze): Vec3 = Vec3(aelos.x, aelos.getY(0.8), aelos.z)

class SbDestruction(
    private val world: ServerLevel,
    private val random: Random,
    private val aelosId: UUID
) {

    private fun aelos(): Breeze {
        val entity = world.getEntity(aelosId)
        if (entity is Breeze) return entity
        throw IllegalStateException("Aelos not found")
    }

    fun setAelosLookingTowards(island: SbIsland) {
        aelos().lookAt(EntityAnchorArgument.Anchor.EYES, island.getCenter())
    }

    fun fireProjectile(island: SbIsland): BreezeWindCharge {
        val aelos = aelos()
        val center = island.getCenter()
        val chargePos = getChargePos(aelos)
        val dir = center.subtract(chargePos)

        val charge = BreezeWindCharge(aelos, world)
        charge.shoot(dir.x, dir.y, dir.z, 0.9f, 0f)
        charge.setPos(chargePos)

        world.addFreshEntity(charge)

        SoundHelper.playSound(aelos.level().server, SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 1.5f, 1.0f)

        return charge
    }

    fun destroyIsland(island: SbIsland, impactPos: Vec3, velocity: Vec3) {
        val explosion = ServerExplosion(
            world, null, null, null,
            impactPos, 25f, false,
            Explosion.BlockInteraction.KEEP
        )

        @Suppress("KotlinConstantConditions")
        val access = explosion as ServerExplosionAccessor

        world.gameEvent(null, GameEvent.EXPLODE, impactPos)
        addEffects(impactPos)

        val dir = velocity.normalize()

        val flags = Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS

        for (pos in access.invokeCalculateExplodedPositions()) {
            if (!island.bounds.contains(pos)) continue

            val state = world.getBlockState(pos)
            if (state.isAir) continue

            world.setBlock(pos, Blocks.AIR.defaultBlockState(), flags)

            if (random.nextFloat() >= LAUNCHED_PERCENTAGE) continue

            val fallingBlock = FallingBlockEntity(EntityTypes.FALLING_BLOCK, world)
            fallingBlock.setPosRaw(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            fallingBlock.time = 1
            FallingBlockAccess.setDropItem(fallingBlock, false)
            FallingBlockAccess.setDestroyedOnLanding(fallingBlock, true)
            FallingBlockAccess.setBlockState(fallingBlock, state)

            VelocityModifier.setVelocity(fallingBlock, dir)

            world.addFreshEntity(fallingBlock)
        }
    }

    private fun addEffects(pos: Vec3) {
        val x = pos.x; val y = pos.y; val z = pos.z

        ParticleHelper.spawnForceParticle(ParticleTypes.GUST, x, y, z, 300, 7.0, 7.0, 7.0, 0.0, PlayerLookup.level(world))
        ParticleHelper.spawnForceParticle(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 30, 10.0, 10.0, 10.0, 0.0, PlayerLookup.level(world))
        ParticleHelper.spawnForceParticle(ParticleTypes.CLOUD, x, y, z, 200, 1.0, 1.0, 1.0, 1.0, PlayerLookup.level(world))

        for (player in PlayerLookup.around(world, pos, 32.0)) {
            val eyePos = player.eyePosition
            val soundPos = eyePos.add(pos.subtract(eyePos).normalize().scale(8.0))
            val sx = soundPos.x; val sy = soundPos.y; val sz = soundPos.z

            player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, sx, sy, sz, 1f, 1.2f)
            player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, sx, sy, sz, 0.5f, 0.5f)
        }
    }
}
