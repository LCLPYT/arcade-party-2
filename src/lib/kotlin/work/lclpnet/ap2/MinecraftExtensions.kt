package work.lclpnet.ap2

import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

fun World.setBlock(pos: BlockPos, block: Block) = setBlockState(pos, block.defaultState)
