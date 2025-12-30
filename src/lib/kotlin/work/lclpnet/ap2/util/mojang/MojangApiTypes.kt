package work.lclpnet.ap2.util.mojang

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import work.lclpnet.ap2.toUndashedString
import work.lclpnet.ap2.uuidFromUndashedString
import java.util.*
import kotlin.io.encoding.Base64

@Serializable
data class Profile(
    @Serializable(with = UuidWithoutDashes::class)
    val id: UUID,
    val name: String,
    val properties: List<Property>
) {
    val texturesProperty: Property?
        get() = properties.find { it.name == "textures" }
}

@Serializable
data class Property(
    val name: String,
    @Serializable(with = Base64Textures::class)
    val value: Textures
)

@Serializable
data class Textures(
    val timestamp: Long,
    @Serializable(with = UuidWithoutDashes::class)
    val profileId: UUID,
    val profileName: String,
    val textures: Map<String, Texture>
) {
    val skinTexture: Texture?
        get() = textures["SKIN"]
}

@Serializable
data class Texture(
    val url: String,
    val metadata: TextureMetadata? = null,
)

@Serializable
data class TextureMetadata(
    val model: String? = null,
)

object Base64Textures : KSerializer<Textures> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "work.lclpnet.ap2.util.mojang.Base64Textures",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: Textures) {
        val json = Json.encodeToString(value)
        val base64 = Base64.encode(json.encodeToByteArray())

        encoder.encodeString(base64)
    }

    override fun deserialize(decoder: Decoder): Textures {
        val base64 = decoder.decodeString()
        val json = Base64.decode(base64).decodeToString()

        return Json.decodeFromString(json)
    }
}

object UuidWithoutDashes : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "work.lclpnet.ap2.util.mojang.UuidWithoutDashes",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toUndashedString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        val hex = decoder.decodeString()

        return uuidFromUndashedString(hex)
    }
}
