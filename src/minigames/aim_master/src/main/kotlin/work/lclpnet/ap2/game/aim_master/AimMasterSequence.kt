package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

class AimMasterSequence {
    val items: MutableList<Item> = ArrayList()

    data class Item(val blockMap: Map<BlockPos, Block>, val target: BlockPos)
}
