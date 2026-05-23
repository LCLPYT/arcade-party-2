package work.lclpnet.ap2.game.mimicry.data

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import work.lclpnet.gaco.ds.BlockBox

data class MimicryRoom(
    val pos: BlockPos,
    val spawn: BlockPos,
    val yaw: Float,
    val buttons: BlockBox
) {
    private var activeButtonPos: BlockPos? = null
    private var prevButtonBase: BlockState? = null

    fun teleport(player: ServerPlayer, world: ServerLevel) {
        val x = spawn.x + 0.5
        val y = spawn.y.toDouble()
        val z = spawn.z + 0.5

        player.teleportTo(world, x, y, z, emptySet(), yaw, 0.0f, true)
    }

    fun buttonIndex(pos: BlockPos) = buttons.posToIndexYZX(pos)

    fun setButtonActive(i: Int, world: ServerLevel) {
        resetActiveButton(world)

        val buttonPos = buttonPos(i)
        val state = world.getBlockState(buttonPos)

        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return

        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val base = buttonPos.relative(facing.opposite)

        activeButtonPos = base
        prevButtonBase = world.getBlockState(base)

        world.setBlockAndUpdate(base, Blocks.LIME_CONCRETE.defaultBlockState())
    }

    fun buttonPos(i: Int): BlockPos = buttons.indexToPosYZX(i)

    fun resetActiveButton(world: ServerLevel) {
        val base = activeButtonPos ?: return
        val prev = prevButtonBase ?: return

        world.setBlockAndUpdate(base, prev)
    }
}
