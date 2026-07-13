package work.lclpnet.ap2.util.scoreboard

import net.minecraft.server.level.ServerPlayer

interface VirtualScoreboardObjective {

    fun add(player: ServerPlayer)

    fun remove(player: ServerPlayer)

    fun update(player: ServerPlayer)

    fun unload()
}
