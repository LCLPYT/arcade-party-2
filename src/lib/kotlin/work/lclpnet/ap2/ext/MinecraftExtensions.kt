package work.lclpnet.ap2.ext

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Position
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Relative
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.map.MapUtil.centeredDouble
import work.lclpnet.ap2.impl.util.EntityUtil
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.util.PositionRotation

fun Level.setBlock(pos: BlockPos, block: Block) = setBlockAndUpdate(pos, block.defaultBlockState())

fun <T : ParticleOptions> ServerLevel.spawnParticles(particle: T, pos: Position, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, speed: Double) =
    sendParticles(particle, pos.x(), pos.y(), pos.z(), count, offsetX, offsetY, offsetZ, speed)

fun ServerLevel.setBlocks(blocks: Iterable<BlockPos>, block: Block) {
    for (pos in blocks) {
        setBlock(pos, block)
    }
}

fun ServerPlayer.setSelectedSlot(slot: Int) = PlayerInventoryAccess.setSelectedSlot(this, slot)

fun ServerPlayer.teleport(pos: BlockPos) = teleportTo(
    level(), pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z + 0.5, emptySet<Relative>(),
    yRot,
    xRot, true)
fun ServerPlayer.teleport(pos: Position) = teleportTo(
    level(), pos.x(), pos.y(), pos.z(), emptySet<Relative>(),
    yRot,
    xRot, true)
fun ServerPlayer.teleport(pos: Position, yaw: Float) = teleportTo(
    level(), pos.x(), pos.y(), pos.z(), emptySet<Relative>(), yaw,
    xRot, true)
fun ServerPlayer.teleport(pos: PositionRotation) = teleportTo(level(), pos.x(), pos.y(), pos.z(), emptySet<Relative>(), pos.yaw, pos.pitch, true)

fun ServerPlayer.playNotifySound(
    sound: SoundEvent, source: SoundSource, x: Double, y: Double, z: Double, volume: Float, pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, x, y, z, volume, pitch)
}

fun ServerPlayer.playNotifySound(
    sound: SoundEvent, source: SoundSource, pos: Position, volume: Float, pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, pos, volume, pitch)
}

fun ServerPlayer.playNotifySound(
    sound: SoundEvent, source: SoundSource, volume: Float, pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, volume, pitch)
}

fun LivingEntity.setAttribute(attribute: Holder<Attribute>, value: Double)
    = EntityUtil.setAttribute(this, attribute, value)

fun LivingEntity.resetAttribute(attribute: Holder<Attribute>)
        = EntityUtil.resetAttribute(this, attribute)

fun Vec3.centered() = Vec3(
    centeredDouble(x),
    centeredDouble(y),
    centeredDouble(z),
)
