package work.lclpnet.ap2.ext.mc

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Position
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Relative
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import work.lclpnet.ap2.impl.util.EntityUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.kibu.hook.util.PositionRotation

fun Level.setBlock(pos: BlockPos, block: Block) = setBlockAndUpdate(
    pos,
    block.defaultBlockState()
)

fun <T : ParticleOptions> ServerLevel.spawnParticles(
    particle: T,
    pos: Position,
    count: Int,
    offsetX: Double,
    offsetY: Double,
    offsetZ: Double,
    speed: Double
) = sendParticles(
    particle,
    pos.x(),
    pos.y(),
    pos.z(),
    count,
    offsetX,
    offsetY,
    offsetZ,
    speed
)

fun ServerLevel.setBlocks(blocks: Iterable<BlockPos>, block: Block) {
    for (pos in blocks) {
        setBlock(pos, block)
    }
}

fun LivingEntity.setAttribute(attribute: Holder<Attribute>, value: Double) =
    EntityUtil.setAttribute(this, attribute, value)

fun LivingEntity.resetAttribute(attribute: Holder<Attribute>) =
    EntityUtil.resetAttribute(this, attribute)

fun Entity.teleport(level: ServerLevel, pos: PositionRotation) = teleportTo(
    level,
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet<Relative>(),
    pos.yaw,
    pos.pitch,
    true
)

fun ItemStack.unbreakable(): ItemStack = ItemHelper.unbreakable(this)
