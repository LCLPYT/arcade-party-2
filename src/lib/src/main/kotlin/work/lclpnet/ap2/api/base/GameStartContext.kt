package work.lclpnet.ap2.api.base

import net.minecraft.server.level.ServerPlayer

interface GameStartContext {
    val participants: Set<ServerPlayer>

    val participantCount: Int
        get() = participants.size
}
