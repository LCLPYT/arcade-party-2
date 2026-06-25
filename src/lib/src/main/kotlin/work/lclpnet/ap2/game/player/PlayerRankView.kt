package work.lclpnet.ap2.game.player

import net.minecraft.server.level.ServerPlayer

interface PlayerRankView {

    fun score(player: ServerPlayer): Int

    fun rank(player: ServerPlayer): Int
}