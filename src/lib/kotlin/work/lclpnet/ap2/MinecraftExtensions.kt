package work.lclpnet.ap2

import net.minecraft.block.Block
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Position
import net.minecraft.world.World
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.util.PositionRotation

fun World.setBlock(pos: BlockPos, block: Block) = setBlockState(pos, block.defaultState)

fun <T : ParticleEffect> ServerWorld.spawnParticles(particle: T, pos: Position, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, speed: Double) =
    spawnParticles(particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed)

fun ServerWorld.setBlocks(blocks: Iterable<BlockPos>, block: Block) {
    for (pos in blocks) {
        setBlock(pos, block)
    }
}

fun ServerPlayerEntity.setSelectedSlot(slot: Int) = PlayerInventoryAccess.setSelectedSlot(this, slot)

fun ServerPlayerEntity.teleport(pos: BlockPos) = teleport(entityWorld, pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z + 0.5, emptySet<PositionFlag>(), yaw, pitch, true)
fun ServerPlayerEntity.teleport(pos: Position) = teleport(entityWorld, pos.x, pos.y, pos.z, emptySet<PositionFlag>(), yaw, pitch, true)
fun ServerPlayerEntity.teleport(pos: Position, yaw: Float) = teleport(entityWorld, pos.x, pos.y, pos.z, emptySet<PositionFlag>(), yaw, pitch, true)
fun ServerPlayerEntity.teleport(pos: PositionRotation) = teleport(entityWorld, pos.x, pos.y, pos.z, emptySet<PositionFlag>(), pos.yaw, pos.pitch, true)