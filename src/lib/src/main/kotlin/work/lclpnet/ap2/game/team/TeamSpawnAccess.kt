package work.lclpnet.ap2.game.team

import work.lclpnet.kibu.hook.util.PositionRotation

fun interface TeamSpawnAccess {

    fun getSpawn(team: Team): PositionRotation?
}
