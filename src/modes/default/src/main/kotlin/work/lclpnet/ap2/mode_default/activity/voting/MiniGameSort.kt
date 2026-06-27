package work.lclpnet.ap2.mode_default.activity.voting

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.kibu.translate.Translations

enum class MiniGameSort(val labelKey: String) {
    OLDEST("ap2.voting.sort.oldest"),
    LATEST("ap2.voting.sort.latest"),
    NAME_AZ("ap2.voting.sort.name_az"),
    NAME_ZA("ap2.voting.sort.name_za");

    fun next(): MiniGameSort = entries[(ordinal + 1) % entries.size]

    fun previous(): MiniGameSort = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * Filters the given mini-games by [search] (matched against the name in the viewer's language) and orders
 * them according to [sort]. The iteration order of [games] is treated as the registration order
 * (oldest first).
 */
fun viewMiniGames(
    games: Collection<MiniGame>,
    player: ServerPlayer,
    translations: Translations,
    search: String,
    sort: MiniGameSort
): List<MiniGame> {
    val nameOf: (MiniGame) -> String = { translations.translate(player, it.titleKey) }

    val filtered = if (search.isBlank()) {
        games.toList()
    } else {
        games.filter { nameOf(it).contains(search.trim(), ignoreCase = true) }
    }

    return when (sort) {
        MiniGameSort.OLDEST -> filtered
        MiniGameSort.LATEST -> filtered.reversed()
        MiniGameSort.NAME_AZ -> filtered.sortedBy { nameOf(it).lowercase() }
        MiniGameSort.NAME_ZA -> filtered.sortedByDescending { nameOf(it).lowercase() }
    }
}
