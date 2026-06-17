package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY
import net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler
import work.lclpnet.kibu.access.entity.EntityUtil.resetAttribute
import work.lclpnet.kibu.access.entity.EntityUtil.setAttribute
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

const val TAG_HEAVY_WEIGHT = "ap2:heavy_weight_egg"
private val DURATION_TICKS = Ticks.seconds(3)

class HeavyWeightItem : SpecialItem {

    private val heavyWeighted = mutableSetOf<UUID>()
    var doubleJumpHandler: DoubleJumpHandler? = null

    override fun id(): String = "heavy_weight_egg"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.EGG)

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            val player = shooter as? ServerPlayer ?: return@registerWith

            if (projectile !is ThrownEgg || !ctx.hasSpecialItem(player, this)) return@registerWith

            projectile.addTag(TAG_HEAVY_WEIGHT)
            ctx.removeSpecialItem(player, this)
        }

        ProjectileHitEntityCallback.HOOK.registerWith(hooks) { projectile, hit ->
            if (!projectile.entityTags().contains(TAG_HEAVY_WEIGHT)) return@registerWith

            val player = hit.entity as? ServerPlayer ?: return@registerWith

            if (heavyWeighted.contains(player.uuid)) return@registerWith

            setHeavyWeighted(player)

            val pos = hit.location
            val world: ServerLevel = player.level()

            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 0.5f, 0.65f)
            world.sendParticles(ParticleTypes.FALLING_NECTAR, pos.x, pos.y + 1, pos.z, 100, 0.25, 0.5, 0.25, 1.0)

            ctx.translations().translateText("heavy_weighted")
                .styled { it.withColor(0xff0000) }
                .sendTo(player, true)

            ctx.scheduler().timeout(DURATION_TICKS) { ->
                removeHeavyWeighted(player)
            }
        }
    }

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        PlayerInventoryAccess.setSelectedSlot(player, 8)
        return InteractionResult.PASS
    }

    private fun setHeavyWeighted(player: ServerPlayer) {
        if (!heavyWeighted.add(player.uuid)) return

        doubleJumpHandler?.disable(player)
        setAttribute(player, GRAVITY, 0.14)
        setAttribute(player, MOVEMENT_SPEED, 0.075)
    }

    private fun removeHeavyWeighted(player: ServerPlayer) {
        if (!heavyWeighted.remove(player.uuid)) return

        doubleJumpHandler?.enable(player)
        resetAttribute(player, GRAVITY)
        resetAttribute(player, MOVEMENT_SPEED)
    }

    fun isHeavyWeighted(player: ServerPlayer): Boolean = heavyWeighted.contains(player.uuid)
}
