package work.lclpnet.ap2.ext.mc

import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import work.lclpnet.ap2.impl.util.ItemHelper

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

fun ItemStack.unbreakable(): ItemStack = ItemHelper.unbreakable(this)

fun ItemStack.enchant(
    enchantment: ResourceKey<Enchantment>,
    level: Int,
    registryAccess: RegistryAccess,
): ItemStack {
    enchant(ItemHelper.getEnchantment(enchantment, registryAccess), level)
    return this
}
