package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.core.hook.RangedWeaponUsedCallback
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.hook.HookRegistrar

private const val BURST_COUNT = 4
private const val BURST_INTERVAL_TICKS = 2

class BurstShotItem : SpecialItem {

    override fun id(): String = "burst_shot"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.BLAZE_POWDER)

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        RangedWeaponUsedCallback.HOOK.registerWith(hooks) { entity, stack, remainingUseTicks ->
            val player = entity as? ServerPlayer ?: return@registerWith

            if (stack != player.inventory.getItem(4)) return@registerWith

            val bow = stack.item as? BowItem ?: return@registerWith

            if (!ctx.hasSpecialItem(player, this)) return@registerWith

            ctx.removeSpecialItem(player, this)

            val useTicks = bow.getUseDuration(stack, player) - remainingUseTicks

            for (i in 1 until BURST_COUNT) {
                ctx.scheduler().timeout(BURST_INTERVAL_TICKS * i) { ->
                    shoot(player, stack, useTicks)
                }
            }
        }
    }

    private fun shoot(player: ServerPlayer, weaponStack: ItemStack, useTicks: Int) {
        val world: ServerLevel = player.level()
        val projectileStack = ItemStack(Items.ARROW)
        val arrow = (Items.ARROW as ArrowItem).createArrow(world, projectileStack, player, weaponStack)
        val useProgress = BowItem.getPowerForTime(useTicks)
        val speed = useProgress * 3.0f

        Projectile.spawnProjectile(arrow, world, projectileStack) { projectile ->
            projectile.shootFromRotation(player, player.xRot, player.yRot, 0.0f, speed, 1f)
        }

        world.playSound(null, player.x, player.y, player.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
            1.0f, 1.0f / (world.random.nextFloat() * 0.4f + 1.2f) + useProgress * 0.5f)
    }
}
