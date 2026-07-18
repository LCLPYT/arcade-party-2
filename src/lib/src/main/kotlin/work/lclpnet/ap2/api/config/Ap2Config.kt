package work.lclpnet.ap2.api.config

import com.google.common.collect.ImmutableList
import org.json.JSONArray
import org.json.JSONObject
import work.lclpnet.config.json.JsonConfig
import java.net.URI
import java.nio.file.Path

class Ap2Config : JsonConfig {
    var mapsSource = listOf<URI>(URI.create("https://assets.lclpnet.work/release/maps/"))
    var songsSource = listOf<URI>(URI.create("https://assets.lclpnet.work/release/songs/"))

    constructor()

    constructor(json: JSONObject) {
        if (json.has("maps_source")) {
            this.mapsSource = readUriList(json, "maps_source")
        }

        if (json.has("songs_source")) {
            this.songsSource = readUriList(json, "songs_source")
        }
    }

    override fun toJson(): JSONObject {
        val json = JSONObject()

        writeUriList(json, "maps_source", mapsSource)
        writeUriList(json, "songs_source", songsSource)

        return json
    }

    companion object {
        private fun writeUriList(json: JSONObject, key: String, uriList: Collection<URI>) {
            val order = JSONArray()

            for (uri in uriList) {
                order.put(uriToString(uri))
            }

            json.put(key, order)
        }

        private fun readUriList(json: JSONObject, key: String): List<URI> {
            val order = json.getJSONArray(key)

            val builder = ImmutableList.builder<URI>()

            for (obj in order) {
                if (obj !is String) continue

                builder.add(stringToUri(obj))
            }

            return builder.build()
        }

        private fun stringToUri(str: String): URI {
            var source = str.replace('\\', '/')

            if (!source.endsWith("/")) {
                source += "/"
            }

            val uri = URI.create(source)

            if (uri.host != null) {
                return uri
            }

            // uri is local path
            val path = if (uri.scheme != null) {
                Path.of(uri)
            } else {
                Path.of(uri.getPath())
            }

            return path.toUri()
        }

        private fun uriToString(uri: URI): String {
            if (uri.host != null) {
                return uri.toString()
            }

            // local path
            val current = Path.of("").toAbsolutePath()

            val sourcePath: Path = if (uri.scheme == null) {
                // uri without scheme
                Path.of(uri.toString()).toAbsolutePath()
            } else {
                // file:/// uri
                Path.of(uri)
            }

            val relativeSourceSource = current.relativize(sourcePath)

            return relativeSourceSource.toString()
        }
    }
}
