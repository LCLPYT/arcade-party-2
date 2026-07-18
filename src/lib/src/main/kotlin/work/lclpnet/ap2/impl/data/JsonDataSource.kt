package work.lclpnet.ap2.impl.data

import org.json.JSONObject
import org.slf4j.Logger
import java.io.InputStream
import java.nio.charset.StandardCharsets

class JsonDataSource(
    private val logger: Logger,
    private val inputProvider: () -> InputStream,
) : DataSource {

    override fun load(consumer: (String, Any) -> Unit) {
        val json = try {
            inputProvider().use { input ->
                val bytes = input.readAllBytes()
                val str = String(bytes, StandardCharsets.UTF_8)

                JSONObject(str)
            }
        } catch (e: Exception) {
            logger.error("Failed to read input stream", e)
            return
        }

        val it = json.keys()

        while (it.hasNext()) {
            val key = it.next()
            val value = json.get(key)

            consumer(key, value)
        }
    }
}
