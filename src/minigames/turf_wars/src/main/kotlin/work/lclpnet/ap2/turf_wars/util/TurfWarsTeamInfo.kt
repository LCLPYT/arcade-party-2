package work.lclpnet.ap2.turf_wars.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.TeamKey
import work.lclpnet.ap2.api.game.team.TeamKeyable
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

data class TurfWarsTeamInfo(
    val spawn: PositionRotation,
    val baseBounds: BlockBox,
    val initialTurf: BlockBox,
    private val teamKey: DyeTeamKey
) : TeamKeyable {

    override fun key(): TeamKey = teamKey

    fun participants(teamManager: TeamManager, participants: Participants): Set<ServerPlayer> =
        teamManager.getTeam(this)
            .map { it.getParticipatingPlayers(participants) }
            .orElseGet { emptySet() }
}