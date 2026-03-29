package work.lclpnet.ap2.ext.mc

import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Relative
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.util.PositionRotation

fun ServerPlayer.setSelectedSlot(slot: Int) = PlayerInventoryAccess.setSelectedSlot(this, slot)

fun ServerPlayer.teleport(pos: BlockPos) = teleportTo(
    level(),
    pos.x.toDouble() + 0.5,
    pos.y.toDouble(),
    pos.z + 0.5,
    emptySet<Relative>(),
    yRot,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: Position) = teleportTo(
    level(),
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet<Relative>(),
    yRot,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: Position, yaw: Float) = teleportTo(
    level(),
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet<Relative>(),
    yaw,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: PositionRotation) = teleportTo(
    level(),
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet<Relative>(),
    pos.yaw,
    pos.pitch,
    true
)

fun ServerPlayer.playNotifySound(
    sound: SoundEvent,
    source: SoundSource,
    x: Double,
    y: Double,
    z: Double,
    volume: Float,
    pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, x, y, z, volume, pitch)
}

fun ServerPlayer.playNotifySound(
    sound: SoundEvent,
    source: SoundSource,
    pos: Position,
    volume: Float,
    pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, pos, volume, pitch)
}

fun ServerPlayer.playNotifySound(
    sound: SoundEvent,
    source: SoundSource,
    volume: Float,
    pitch: Float
) {
    ServerPlayerAccess.playSoundToPlayer(this, sound, source, volume, pitch)
}