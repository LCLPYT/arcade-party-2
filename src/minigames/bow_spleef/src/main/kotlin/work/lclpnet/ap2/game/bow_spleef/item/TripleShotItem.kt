package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import work.lclpnet.ap2.core.hook.RangedWeaponUsedCallback
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.kibu.hook.HookRegistrar

class TripleShotItem : SpecialItem {

    override val id = "triple_shot"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.ARROW, 3)

    override fun onPickedUp(player: ServerPlayer, stack: ItemStack, ctx: SpecialItemContext) {
        addEnchant(player.inventory.getItem(4), player.level().registryAccess())
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.4f, 1.35f)
    }

    override fun onDropped(player: ServerPlayer) {
        removeEnchant(player.inventory.getItem(4))
    }

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        RangedWeaponUsedCallback.HOOK.registerWith(hooks) { entity, stack, _ ->
            val player = entity as? ServerPlayer ?: return@registerWith

            if (stack != player.inventory.getItem(4) || !ctx.hasSpecialItem(player, this)) return@registerWith

            removeEnchant(stack)
            ctx.removeSpecialItem(player, this)
        }
    }

    private fun addEnchant(bow: ItemStack, registryManager: RegistryAccess) {
        val multiShot = ItemHelper.getEnchantment(Enchantments.MULTISHOT, registryManager)
        bow.enchant(multiShot, 1)
        bow.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    private fun removeEnchant(stack: ItemStack) {
        EnchantmentHelper.updateEnchantments(stack) { builder ->
            builder.removeIf { enchant ->
                enchant.unwrapKey().orElse(null) == Enchantments.MULTISHOT
            }
        }
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
    }
}
