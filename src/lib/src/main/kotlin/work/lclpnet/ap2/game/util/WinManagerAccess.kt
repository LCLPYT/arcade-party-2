package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer

interface WinManagerAccess {

    fun draw()

    fun win(player: ServerPlayer)

    fun win(players: Set<ServerPlayer>)
}