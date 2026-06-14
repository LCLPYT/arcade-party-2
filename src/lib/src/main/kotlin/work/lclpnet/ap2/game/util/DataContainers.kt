package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntDataContainer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.ScoreTimeDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.data.type.TeamRef

fun <T : DataContainer<ServerPlayer, PlayerRef>> MiniGameInstance.useDataContainer(
    factory: (SubjectRefFactory<ServerPlayer, PlayerRef>) -> T
): T {
    val data = factory(PlayerRef::create)

    for (player in players()) {
        data.identityIfAbsent(player)
    }

    return data
}

fun <T : DataContainer<Team, TeamRef>> MiniGameInstance.useDataContainer(
    teamManager: TeamManager,
    factory: (SubjectRefFactory<Team, TeamRef>) -> T
): T {
    val data = factory { team ->
        TeamRef(team.key(), gameHandle.translations)
    }

    for (team in teamManager.teams) {
        data.identityIfAbsent(team)
    }

    return data
}

fun <T, Ref : SubjectRef> MiniGameInstance.finaleCompatibleIntScoreContainer(
    refs: SubjectRefFactory<T, Ref>
): IntDataContainer<T, Ref> = when {
    gameHandle.isFinale -> ScoreTimeDataContainer(refs)
    else -> IntScoreDataContainer(refs)
}
