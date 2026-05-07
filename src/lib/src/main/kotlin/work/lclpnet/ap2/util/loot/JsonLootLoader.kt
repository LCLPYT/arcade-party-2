package work.lclpnet.ap2.util.loot

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import org.slf4j.Logger
import java.nio.charset.StandardCharsets

class JsonLootLoader(
    val logger: Logger,
) {

    @JvmOverloads
    fun fromResource(resourceMember: Class<*>, resource: String = "/loot/containers.json"): LootTable? {
        resourceMember.getResourceAsStream(resource).use {
            if (it == null) return@use null

            val content = String(it.readAllBytes(), StandardCharsets.UTF_8)

            return fromJson(content)
        }

        return null
    }

    fun fromJson(content: String): LootTable? {
        val json = Gson().fromJson(content, JsonObject::class.java)

        return LootTable.CODEC.decode(JsonOps.INSTANCE, json)
            .resultOrPartial { err -> logger.error("Failed to parse loot table: {}", err) }
            .map { res -> res.first }
            .orElse(null)
    }
}