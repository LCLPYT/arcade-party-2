package work.lclpnet.ap2.game.kit

import net.minecraft.server.level.ServerPlayer

class ProxyKitReadView : KitReadView {
    private var delegate: KitReadView? = null

    fun inject(delegate: KitReadView) {
        this.delegate = delegate
    }

    override fun getKit(player: ServerPlayer): Kit {
        return delegate().getKit(player)
    }

    private fun delegate(): KitReadView {
        val delegate = this.delegate

        requireNotNull(delegate) { "No delegate set" }

        return delegate
    }
}
