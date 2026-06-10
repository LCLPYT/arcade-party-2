package work.lclpnet.ap2.util

import kotlinx.coroutines.CoroutineDispatcher
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that runs every continuation on the Minecraft server thread.
 * Used to drive mini-game bootstrap coroutines while preserving the server-thread execution contract.
 */
class MinecraftDispatcher(private val server: MinecraftServer) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        server.execute(block)
    }
}
