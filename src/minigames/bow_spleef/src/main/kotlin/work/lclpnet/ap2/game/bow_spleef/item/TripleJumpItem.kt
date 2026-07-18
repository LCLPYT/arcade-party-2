package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.kibu.hook.util.PlayerUtils
import java.util.*
import kotlin.math.ceil

private const val USES = 1

class TripleJumpItem : SpecialItem {

    private val tripleJump = mutableSetOf<UUID>()

    override val id = "triple_jump"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.GOLDEN_BOOTS, 3)

    override fun onDropped(player: ServerPlayer) {
        tripleJump.remove(player.uuid)
    }

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        PlayerUtils.syncPlayerItems(player)
        return InteractionResult.FAIL
    }

    fun handleExtraJump(player: ServerPlayer, ctx: SpecialItemContext): Boolean {
        val uuid = player.uuid

        if (uuid in tripleJump) {
            tripleJump.remove(uuid)

            val stack = player.inventory.getItem(8)
            val maxDamage = stack.maxDamage
            val damage = stack.damageValue
            val dmg = ceil(maxDamage.toFloat() / USES).toInt()

            if (damage + dmg >= maxDamage) {
                ctx.removeSpecialItem(player, this)
            } else {
                if (player.inventory.selectedSlot == 8) {
                    stack.hurtAndBreak(dmg, player, EquipmentSlot.MAINHAND)
                } else {
                    stack.hurtWithoutBreaking(dmg, player)
                }
            }

            return false
        }

        tripleJump.add(uuid)
        return true
    }
}
