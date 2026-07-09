package work.lclpnet.ap2.api.stats

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.GenericGameResult
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.translate.text.TranslatedText

class FFAStatsManager(stats: StatSet) : BaseStatsManager<ServerPlayer, PlayerRef>(stats, PlayerRef::create), StatsManager<PlayerRef> {

    override fun fillDefaults(result: GenericGameResult<PlayerRef>) {
        fillDefaults(result.playerResults.map { (ref, _) -> ref })
    }

    override fun getResult(
        summary: GameSummary,
        result: GenericGameResult<PlayerRef>,
        details: Map<PlayerRef, TranslatedText>
    ): FFAStatsResult {
        val results: Map<PlayerRef, Stats> = getEntries()
        val statsView = StatsView(stats, result.playerResults, results, details)

        return FFAStatsResult(
            summary = summary,
            view = statsView,
        )
    }
}

class FFAStatsResult(
    override val summary: GameSummary,
    val view: StatsView<PlayerRef>,
) : StatsResult {
    override val type = "ffa"
}
