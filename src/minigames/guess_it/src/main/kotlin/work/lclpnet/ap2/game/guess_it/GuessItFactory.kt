package work.lclpnet.ap2.game.guess_it

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.guess_it.data.SoundSubtitles
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.gaco.ds.IndexedSet
import java.nio.charset.StandardCharsets
import java.util.*

class GuessItFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val soundSubtitles = SoundSubtitles.load()

        val mannequinUuids = withContext(Dispatchers.IO) {
            loadMannequinUuidsSync(handle)
        }

        return GuessItInstance(handle, level, map, soundSubtitles.await(), IndexedSet(mannequinUuids))
    }

    private fun loadMannequinUuidsSync(handle: MiniGameHandle): Set<UUID> {
        val input = javaClass.getResourceAsStream("/mannequin_players.json") ?: return emptySet()

        try {
            input.use {
                val content = String(input.readAllBytes(), StandardCharsets.UTF_8)
                val json = JSONObject(content)
                val array = json.getJSONArray("uuids")

                val uuids = mutableSetOf<UUID>()

                for (uuid in array) {
                    if (uuid !is String) continue

                    try {
                        uuids.add(UUID.fromString(uuid))
                    } catch (e: IllegalArgumentException) {
                        handle.logger.error("Malformed uuid: {}", uuid, e)
                    }
                }
                return uuids
            }
        } catch (t: Throwable) {
            handle.logger.error("Failed to load mannequin player uuids", t)
            return emptySet()
        }
    }
}