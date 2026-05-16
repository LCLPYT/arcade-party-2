package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.api.game.team.Team

fun interface CCFuelListener {
    fun onAddFuel(player: ServerPlayer, pos: BlockPos, team: Team, stack: ItemStack)
}
