package work.lclpnet.ap2.game.kit

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.access.misc.CustomNbt

open class SingleItemKit protected constructor(
    handle: KitHandle,
    id: String,
    val item: Item,
    private val count: Int
) : BaseKit(handle, id) {
    override fun createItemStack(manager: RegistryAccess): ItemStack {
        val stack = ItemStack(item)

        configureItemStack(stack)

        return stack
    }

    open fun configureItemStack(stack: ItemStack) {
        CustomNbt.set(stack, KIT_CODEC, id)
    }

    override fun equip(player: ServerPlayer, options: KitOptions) {
        val stack = handle.createItemStack(this, player)
        stack.count = count

        player.inventory.setItem(options.mainItemSlot, stack)
    }

    override fun unequip(player: ServerPlayer, options: KitOptions) {
        player.inventory.removeItemNoUpdate(options.mainItemSlot)
    }

    companion object {
        private val KIT_CODEC: MapCodec<String> = Codec.STRING.fieldOf("ap2:kit")

        @JvmStatic
        fun getId(stack: ItemStack): String? =
            CustomNbt.get(stack, KIT_CODEC).orElse(null)

        @JvmStatic
        fun get(stack: ItemStack, kitManager: KitManager): SingleItemKit? = getId(stack)
            ?.let { kitManager.byId(it) }
            ?.let { it as? SingleItemKit }
    }
}
