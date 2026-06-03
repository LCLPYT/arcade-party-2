package work.lclpnet.ap2.game.kit

import net.minecraft.server.level.ServerPlayer

interface KitReadView {
    fun getKit(player: ServerPlayer): Kit

    fun hasKitEquipped(player: ServerPlayer, kit: Kit): Boolean =
        getKit(player) == kit
}

inline fun <reified T> KitReadView.hasKitEquipped(player: ServerPlayer): Boolean =
    getKit(player) is T