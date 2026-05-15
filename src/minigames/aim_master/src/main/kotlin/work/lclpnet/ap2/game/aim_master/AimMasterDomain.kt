package work.lclpnet.ap2.game.aim_master

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.ObserverBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.game.util.RayCaster

class AimMasterDomain(
    private val spawn: BlockPos,
    private val yaw: Float,
    private val world: ServerLevel
) {
    var currentTarget: BlockPos? = null
        private set

    fun teleport(player: ServerPlayer) {
        player.teleportTo(world, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, emptySet(), yaw, 0f, true)
    }

    fun setBlocks(item: AimMasterSequence.Item, player: ServerPlayer) {
        val blockMap = item.blockMap
        for ((pos, block) in blockMap) {
            val offsetPos = pos.offset(spawn)
            currentTarget = item.target.offset(spawn)
            world.setBlockAndUpdate(offsetPos, getBlockState(block))

            val stack = ItemStack(blockMap[item.target]!!)
            stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                .withStyle { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })
            player.inventory.setItem(4, stack)
        }
    }

    fun removeBlocks(item: AimMasterSequence.Item) {
        for (pos in item.blockMap.keys) {
            world.setBlockAndUpdate(pos.offset(spawn), getBlockState(Blocks.AIR))
        }
        currentTarget = null
    }

    fun rayCaster(player: ServerPlayer, radius: Int): Boolean {
        val start = player.eyePosition
        val end = player.eyePosition.add(player.lookAngle.scale(radius * 2.0))
        val res = RayCaster.rayCast(start, end) { it == currentTarget }
        return res.type == HitResult.Type.BLOCK
    }
}

private fun getBlockState(block: Block): BlockState = when (block) {
    is LeavesBlock -> block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
    is ObserverBlock -> block.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH)
    else -> block.defaultBlockState()
}
