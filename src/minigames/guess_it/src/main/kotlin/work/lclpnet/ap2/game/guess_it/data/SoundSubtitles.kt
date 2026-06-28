package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class SoundSubtitles(val soundEvents: Set<SoundEvent>) {

    companion object {
        fun load(): CompletableFuture<SoundSubtitles> = CompletableFuture.supplyAsync {
            try {
                return@supplyAsync loadSync()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        @Throws(IOException::class)
        private fun loadSync(): SoundSubtitles {
            val content: String?

            SoundSubtitles::class.java.getResourceAsStream("/assets/minecraft/lang/en_us.json").use { input ->
                if (input == null) {
                    throw FileNotFoundException("Cannot find language file")
                }

                content = String(input.readAllBytes(), StandardCharsets.UTF_8)
            }

            val json = JSONObject(content)

            val sounds = loadSoundsFromJson(json)

            return SoundSubtitles(sounds)
        }

        private fun loadSoundsFromJson(json: JSONObject): Set<SoundEvent> {
            val soundEvents = HashSet<SoundEvent>()
            val it = json.keys()

            val prefix = "subtitles."

            while (it.hasNext()) {
                val key = it.next()

                if (!key.startsWith(prefix)) continue

                val rest: String = key.substring(prefix.length)
                val id = Identifier.parse(rest)
                val soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(id)

                if (soundEvent != null) {
                    soundEvents.add(soundEvent)
                }
            }

            return soundEvents
        }
    }
}
