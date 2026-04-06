package work.lclpnet.ap2.mode_default.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.MinecraftServer
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.ServerMessageHooks
import work.lclpnet.kibu.translate.Translations
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

class WholesomeChatManager(
    private val server: MinecraftServer,
    private val translations: Translations,
) {

    fun init(hooks: HookRegistrar) {
        hooks.registerHook(ServerMessageHooks.ALLOW_CHAT_MESSAGE) { message, _, params ->
            if (!isToxic(message)) {
                return@registerHook true
            }

            val key = randomWholesomeKey()
            broadcastWholesomeMessage(key, params)
            false
        }
    }

    private fun isToxic(message: PlayerChatMessage): Boolean {
        val canonical = message.signedBody().content()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")

        return canonical == "ggez" || canonical == "bg"
    }

    private fun randomWholesomeKey(): String {
        val index = ThreadLocalRandom.current().nextInt(WHOLESOME_MESSAGE_KEYS.size)
        return WHOLESOME_MESSAGE_KEYS[index]
    }

    private fun broadcastWholesomeMessage(key: String, params: ChatType.Bound) {
        for (player in PlayerLookup.all(server)) {
            val message = translations.translateText(key).translateFor(player)
            player.connection.sendDisguisedChatMessage(message, params)
        }
    }

    companion object {
        private val WHOLESOME_MESSAGE_KEYS = listOf(
            "ap2.chat.wholesome.1",
            "ap2.chat.wholesome.2",
            "ap2.chat.wholesome.3",
            "ap2.chat.wholesome.4",
            "ap2.chat.wholesome.5",
            "ap2.chat.wholesome.6",
            "ap2.chat.wholesome.7",
            "ap2.chat.wholesome.8",
        )
    }
}
