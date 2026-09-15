package work.lclpnet.ap2.impl.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import work.lclpnet.ap2.ApConstants
import work.lclpnet.gaco.ds.queue.JsonFileQueuePersistence
import work.lclpnet.gaco.ds.queue.SeamlessQueue
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapManager
import java.util.*
import java.util.stream.Collectors
import kotlin.math.floor
import kotlin.math.max

class SeamlessMapRandomizer(
    private val mapManager: MapManager,
    private val random: Random,
    private val logger: Logger,
) : MapRandomizer {
    private var forcedMap: Identifier? = null

    override suspend fun nextMap(gameId: Identifier): GameMap {
        val mapIds = mapManager.collection()
            .mapIdsWithPrefix(gameId)
            .collect(Collectors.toSet())

        if (mapIds.isEmpty()) {
            throw NoSuchElementException("No maps found for game: $gameId")
        }

        val forcedMapId = forcedMap

        if (forcedMapId != null && forcedMapId in mapIds) {
            // TODO fallback to random map if unavailable
            return getMapById(forcedMapId)
        }

        return getRandomMap(gameId, mapIds)
    }

    private suspend fun getRandomMap(gameId: Identifier, mapIds: Set<Identifier>): GameMap {
        require(mapIds.isNotEmpty()) { "Map IDs must not be empty" }

        val margin = max(0, floor((mapIds.size * MARGIN_PERCENT).toDouble()).toInt())

        return withContext(Dispatchers.IO) {
            val queueId = gameId.withSuffix("/map_queue")

            val queuePersistence = JsonFileQueuePersistence.create(
                ApConstants.RUNTIME_CONFIG_ID,
                queueId,
                Identifier.CODEC,
                logger
            )

            val transfer = queuePersistence.restore()

            val queue = SeamlessQueue(mapIds, random, margin, transfer)

            val next = queue.next()

            val map = getMapById(next)
            queue.pushElement(next)
            queuePersistence.store(queue.transfer())

            map
        }
    }

    override fun forceMap(mapId: Identifier?) {
        this.forcedMap = mapId
    }

    private fun getMapById(mapId: Identifier): GameMap {
        return mapManager.collection()
            .getMap(mapId)
            .orElse(null)
            ?: throw NoSuchElementException("Map '$mapId' not found")
    }

    companion object {
        private const val MARGIN_PERCENT = 0.35f
    }
}
