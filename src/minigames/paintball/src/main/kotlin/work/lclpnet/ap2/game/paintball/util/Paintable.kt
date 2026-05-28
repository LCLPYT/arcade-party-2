package work.lclpnet.ap2.game.paintball.util

import net.minecraft.world.level.block.Block
import work.lclpnet.ap2.api.game.team.DyeTeamKey

fun interface Paintable {
    fun blockFor(team: DyeTeamKey): Block
}
