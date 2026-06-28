package work.lclpnet.ap2.turf_wars.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamKeyable
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

data class TurfWarsTeamInfo(
    val spawn: PositionRotation,
    val baseBounds: BlockBox,
    val initialTurf: BlockBox,
    override val key: DyeTeamKey
) : TeamKeyable {

    fun isMember(player: ServerPlayer, teamManager: TeamManager): Boolean {
        val team = teamManager.getTeam(player) ?: return false

        return team.key == key
    }
}