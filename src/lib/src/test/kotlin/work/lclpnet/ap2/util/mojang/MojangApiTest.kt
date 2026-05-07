package work.lclpnet.ap2.util.mojang

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*

class MojangApiTest {

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeProfile() {
        val profile = jsonFormat.decodeFromString<Profile>("""
            {
              "id" : "853c80ef3c3749fdaa49938b674adae6",
              "name" : "jeb_",
              "properties" : [ {
                "name" : "textures",
                "value" : "ewogICJ0aW1lc3RhbXAiIDogMTc2NzA1MTE4MzU4NiwKICAicHJvZmlsZUlkIiA6ICI4NTNjODBlZjNjMzc0OWZkYWE0OTkzOGI2NzRhZGFlNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJqZWJfIiwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdmZDliYTQyYTdjODFlZWVhMjJmMTUyNDI3MWFlODVhOGUwNDVjZTBhZjVhNmFlMTZjNjQwNmFlOTE3ZTY4YjUiCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzllNTA3YWZjNTYzNTk5NzhhM2ViM2UzMjM2NzA0MmI4NTNjZGRkMDk5NWQxN2QwZGE5OTU2NjI5MTNmYjAwZjciCiAgICB9CiAgfQp9"
              } ],
              "profileActions" : [ ]
            }
        """.trimIndent())

        val uuid = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")
        assertEquals(uuid, profile.id)
        assertEquals("jeb_", profile.name)

        val texturesProperty = profile.texturesProperty ?: fail { "Missing textures property" }

        assertEquals("textures", texturesProperty.name)

        val textures = texturesProperty.value
        assertEquals(1767051183586, textures.timestamp)
        assertEquals(uuid, textures.profileId)
        assertEquals("jeb_", textures.profileName)

        val skin = textures.skinTexture ?: fail { "Expected skin texture" }

        assertEquals("http://textures.minecraft.net/texture/7fd9ba42a7c81eeea22f1524271ae85a8e045ce0af5a6ae16c6406ae917e68b5", skin.url)
        assertNull(skin.metadata)

        val cape = textures.textures["CAPE"] ?: fail { "Expected cape texture" }

        assertEquals("http://textures.minecraft.net/texture/9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7", cape.url)
        assertNull(skin.metadata)
    }
}