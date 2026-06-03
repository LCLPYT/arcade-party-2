package work.lclpnet.ap2.api.stats

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.GameInfo
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapDescriptor

class FFAStatsManager(stats: StatSet) : BaseStatsManager<ServerPlayer, PlayerRef>(stats, PlayerRef::create), StatsManager<PlayerRef> {

    override fun getResult(gameInfo: GameInfo, map: GameMap, result: GenericGameResult<PlayerRef>): FFAStatsResult {
        val results: Map<PlayerRef, Stats> = getEntries()
        val statsView = StatsView(stats, result.playerResults, results)

        return FFAStatsResult(
            gameId = gameInfo.id,
            mapId = map.descriptor,
            view = statsView,
        )
    }
}

class FFAStatsResult(
    override val gameId: Identifier,
    override val mapId: MapDescriptor,
    val view: StatsView<PlayerRef>,
) : StatsResult {
    override val type = "ffa"
}
