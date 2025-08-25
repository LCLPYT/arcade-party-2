package work.lclpnet.ap2

import net.minecraft.block.Block
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Position
import net.minecraft.world.World

fun World.setBlock(pos: BlockPos, block: Block) = setBlockState(pos, block.defaultState)

fun <T : ParticleEffect> ServerWorld.spawnParticles(particle: T, pos: Position, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, speed: Double) =
    spawnParticles(particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed)