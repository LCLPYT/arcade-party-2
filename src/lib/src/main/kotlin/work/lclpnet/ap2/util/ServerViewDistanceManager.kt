package work.lclpnet.ap2.util

import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer

class ServerViewDistanceManager(
    private val server: MinecraftServer,
    val defaultViewDistance: Int = 10,
) {
    fun setViewDistance(viewDistance: Int) {
        (server as? DedicatedServer)?.setViewDistance(viewDistance.coerceIn(2..32))
    }

    fun reset() {
        setViewDistance(defaultViewDistance)
    }
}