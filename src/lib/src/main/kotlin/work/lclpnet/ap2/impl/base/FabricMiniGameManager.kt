package work.lclpnet.ap2.impl.base

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableBiMap
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import work.lclpnet.ap2.game.GameInfo
import work.lclpnet.ap2.game.MiniGame
import java.nio.file.Path
import java.util.*
import java.util.function.Function

/**
 * A [MiniGameManager] that retrieves games from Fabric entry points of type "ap2:minigame".
 */
class FabricMiniGameManager(logger: Logger) : MiniGameManager {

    private val gamesById: BiMap<Identifier, MiniGame>
    override val games: Set<MiniGame>  // use a separate set that is backed by a LinkedHashSet (order preserving)

    init {
        val miniGames = FabricLoader.getInstance()
            .getEntrypointContainers(MINIGAME_ENTRYPOINT, MiniGame::class.java)
            .stream()
            .sorted(minigameOrdering())
            .map { it.getEntrypoint() }
            .toList()

        val registry = LinkedHashSet(miniGames)

        val byId = HashBiMap.create<Identifier, MiniGame>()

        for (miniGame in registry) {
            val id = miniGame.id

            if (byId.put(id, miniGame) != null) {
                logger.warn("Mini game id collision with id {}", id)
            }
        }

        this.gamesById = ImmutableBiMap.copyOf(byId)
        this.games = registry.toSet()
    }

    private fun minigameOrdering(): Comparator<EntrypointContainer<MiniGame>> {
        return Comparator.comparingLong { container ->
            val timestamp = container.provider.metadata.getCustomValue("timestamp")

            if (timestamp != null) timestamp.asNumber.toLong() else Long.MAX_VALUE
        }
    }

    val gameSources: List<MiniGameSource>
        get() = FabricLoader.getInstance()
            .getEntrypointContainers(MINIGAME_ENTRYPOINT, MiniGame::class.java)
            .stream()
            .map { container ->
                MiniGameSource(container.getEntrypoint(), container.provider.rootPaths)
            }
            .toList()

    override fun getGame(gameId: Identifier): MiniGame? =
        gamesById[gameId]

    override val gameCodec: Codec<MiniGame> = Identifier.CODEC.comapFlatMap(
        Function { id ->
            val game = getGame(id)

            if (game != null) {
                DataResult.success(game)
            } else {
                DataResult.error { "Unknown game with id $id" }
            }
        },
        GameInfo::id
    )

    data class MiniGameSource(
        val game: MiniGame,
        val rootPaths: List<Path>,
    )

    companion object {
        const val MINIGAME_ENTRYPOINT = "ap2:minigame"
    }
}
