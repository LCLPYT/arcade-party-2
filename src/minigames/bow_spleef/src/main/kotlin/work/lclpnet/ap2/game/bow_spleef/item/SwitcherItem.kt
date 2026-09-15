package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.HookRegistrar

const val TAG_SWITCHER = "ap2:switcher"

class SwitcherItem : SpecialItem {

    override val id = "switcher"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.SNOWBALL)

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            val player = shooter as? ServerPlayer ?: return@registerWith

            if (projectile !is Snowball || !ctx.hasSpecialItem(player, this)) return@registerWith

            projectile.addTag(TAG_SWITCHER)
            ctx.removeSpecialItem(player, this)
        }

        ProjectileHitEntityCallback.HOOK.registerWith(hooks) { projectile, hit ->
            if (!projectile.entityTags().contains(TAG_SWITCHER)) return@registerWith

            val shooter = projectile.owner as? ServerPlayer ?: return@registerWith
            val victim = hit.entity as? ServerPlayer ?: return@registerWith

            val victimPos = victim.position()
            val victimYaw = victim.yRot
            val victimPitch = victim.xRot

            val world: ServerLevel = shooter.level()
            victim.teleportTo(world, shooter.x, shooter.y, shooter.z, emptySet(), shooter.yRot, shooter.xRot, true)
            shooter.teleportTo(world, victimPos.x(), victimPos.y(), victimPos.z(), emptySet(), victimYaw, victimPitch, true)

            victim.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 2f)
            shooter.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 2f)
        }
    }

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        PlayerInventoryAccess.setSelectedSlot(player, 8)
        return InteractionResult.PASS
    }
}
