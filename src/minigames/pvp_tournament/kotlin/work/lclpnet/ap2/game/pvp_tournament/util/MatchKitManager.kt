package work.lclpnet.ap2.game.pvp_tournament.util

import work.lclpnet.ap2.game.pvp_tournament.gen.Match
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.gaco.ds.WeightedList
import kotlin.random.Random
import kotlin.random.asJavaRandom

class MatchKitManager(
    val kits: WeightedList<Kit>,
) {
    init {
        require(kits.isNotEmpty()) { "Kits must not be empty" }
    }

    private val kitPlayCountByPlayer = mutableMapOf<PlayerRef, MutableMap<Kit, Int>>()
    private val matchKits = mutableMapOf<Match, Kit>()

    operator fun get(match: Match): Kit {
        val kit = matchKits[match]

        if (kit != null) {
            return kit
        }

        synchronized(this) {
            return matchKits[match] ?: pickKit(match).also {
                matchKits[match] = it
            }
        }
    }

    private fun getKitPlayCount(player: PlayerRef?, kit: Kit): Int {
        if (player == null) return 0

        val kitPlayCount = kitPlayCountByPlayer[player] ?: mapOf()

        return kitPlayCount[kit] ?: 0
    }

    private fun pickKit(match: Match): Kit {
        // try to pick a kit which both players have played least
        val combinedPlayCount = kits.map {
            it to getKitPlayCount(match.leftPlayer, it) + getKitPlayCount(match.rightPlayer, it)
        }

        val minPlayCount = combinedPlayCount.minOf { (_, count) -> count }

        val kitChoices = combinedPlayCount
            .filter { (_, count) -> count == minPlayCount }
            .map { (kit, _) -> kit }
            .toSet()

        return kits
            .filter { kitChoices.contains(it) }
            .getRandomElement(Random.asJavaRandom())!!
    }
}