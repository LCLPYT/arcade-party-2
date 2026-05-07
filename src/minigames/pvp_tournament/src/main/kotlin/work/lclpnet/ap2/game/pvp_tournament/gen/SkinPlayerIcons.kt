package work.lclpnet.ap2.game.pvp_tournament.gen

import com.mojang.authlib.GameProfile
import kotlinx.serialization.json.Json
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.util.mojang.Profile
import work.lclpnet.ap2.util.mojang.Property
import work.lclpnet.ap2.util.mojang.SkinFetcher
import work.lclpnet.ap2.util.mojang.Textures
import java.awt.image.BufferedImage
import kotlin.io.encoding.Base64

class SkinPlayerIcons(val skinFetcher: SkinFetcher) : PlayerIcons {

    val cache = mutableMapOf<PlayerRef, BufferedImage>()

    override suspend fun get(player: PlayerRef): BufferedImage {
        val cached = cache[player]

        if (cached != null) {
            return cached
        }

        val skin = requireNotNull(skinFetcher.fetchSkin(player.uuid())) {
            "Skin of $player or default skin could not be loaded"
        }

        val faceTexture = SkinFetcher.getFaceTexture(skin)

        cache[player] = faceTexture

        return faceTexture
    }

    suspend fun preload(gameProfile: GameProfile) {
        skinFetcher.fetchSkin(gameProfile.toProfile())
    }
}

fun GameProfile.toProfile(): Profile {
    return Profile(
        id,
        name,
        properties.entries().map { (_, prop) ->
            val json = Base64.decode(prop.value).decodeToString()
            val textures: Textures = Json.decodeFromString(json)

            Property(prop.name, textures)
        }
    )
}