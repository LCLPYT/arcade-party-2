package work.lclpnet.ap2.dragons.kit

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.ap2.game.kit.setupOnUse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FlasherKit(
    handle: KitHandle,
    val cooldown: Duration = 15.seconds,
    uses: Int = 3,
) : SingleItemKit(handle, ID, Items.EMERALD, uses) {

    override fun init(options: KitOptions) {
        setupOnUse(::useItem)
    }

    private fun useItem(player: ServerPlayer, stack: ItemStack) {
        stack.consume(1, player)
        player.cooldowns.addCooldown(stack, cooldown.inWholeTicks.toInt())

    }

    companion object {
        const val ID = "flasher"
    }
}