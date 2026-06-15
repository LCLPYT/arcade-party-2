package work.lclpnet.ap2.ext.mc

import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.util.PositionRotation

fun ServerPlayer.setSelectedSlot(slot: Int) = PlayerInventoryAccess.setSelectedSlot(this, slot)

fun ServerPlayer.teleport(pos: BlockPos, level: ServerLevel = level()) = teleportTo(
    level,
    pos.x.toDouble() + 0.5,
    pos.y.toDouble(),
    pos.z + 0.5,
    emptySet(),
    yRot,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: Position, level: ServerLevel = level()) = teleportTo(
    level,
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet(),
    yRot,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: Position, yaw: Float, level: ServerLevel = level()) = teleportTo(
    level,
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet(),
    yaw,
    xRot,
    true
)

fun ServerPlayer.teleport(pos: PositionRotation, level: ServerLevel = level()) = teleportTo(
    level,
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet(),
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