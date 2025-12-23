package work.lclpnet.ap2.util.loot

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container

interface LootFiller {

    fun fill(pos: BlockPos, level: ServerLevel, container: Container)
}