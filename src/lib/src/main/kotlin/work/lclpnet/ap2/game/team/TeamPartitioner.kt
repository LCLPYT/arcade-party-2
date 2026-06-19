package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer

fun interface TeamPartitioner {

    fun splitIntoTeams(players: Set<ServerPlayer>, teams: Set<Team>): Map<ServerPlayer, Team>
}
