package work.lclpnet.ap2.turf_wars.util

import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.TeamKeyable
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

data class TurfWarsTeamInfo(
    val spawn: PositionRotation,
    val baseBounds: BlockBox,
    val initialTurf: BlockBox,
    private val teamKey: DyeTeamKey
) : TeamKeyable {

    override fun key(): DyeTeamKey = teamKey
}