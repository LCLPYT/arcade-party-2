package work.lclpnet.ap2.game.paintball.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper

private const val HEAL_PERCENT = 0.75f
private const val ABSORPTION_AMOUNT = 2f

class MedKitItem(private val onUsed: (ServerPlayer) -> Unit) : SpecialItem {

    override val id = "med_kit"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack {
        val stack = ItemStack(Items.POTION)
        stack.set(DataComponents.POTION_CONTENTS, PotionContents(Potions.STRONG_HEALING))
        return stack
    }

    override fun shouldTransferToInventory(player: ServerPlayer) = false

    override fun onPickedUp(player: ServerPlayer, stack: ItemStack, ctx: SpecialItemContext) {
        if (player.health >= player.maxHealth) {
            val absorption = player.absorptionAmount + ABSORPTION_AMOUNT
            player.setAttribute(Attributes.MAX_ABSORPTION, absorption.toDouble())
            player.absorptionAmount = absorption
        } else {
            player.heal(player.maxHealth * HEAL_PERCENT)
        }

        SoundHelper.playSoundAt(player, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 1f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.HEART, 50, 1.0, 1.0, 1.0, 0.0)

        onUsed(player)
    }
}
