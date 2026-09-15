package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.game.bow_spleef.Impact
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.world.ExplosionUtil
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookRegistrar

const val TAG_EXPLOSIVE = "ap2:explosive"

class ExplodeAmmoItem(private val impactHook: Hook<Impact>) : SpecialItem {

    override val id = "explode_ammo"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.TNT)

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            val player = shooter as? ServerPlayer ?: return@registerWith

            if (projectile !is Arrow || !ctx.hasSpecialItem(player, this)) return@registerWith

            projectile.addTag(TAG_EXPLOSIVE)
            ctx.removeSpecialItem(player, this)
            player.level().playSound(null, player.x, player.eyeY, player.z, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.5f, 1.75f)
        }

        impactHook.registerWith(hooks) { projectile, blockPos ->
            if (projectile.level() !is ServerLevel || !projectile.entityTags().contains(TAG_EXPLOSIVE)) return@registerWith

            val world = projectile.level() as ServerLevel

            val behaviour = object : ExplosionDamageCalculator() {
                override fun getKnockbackMultiplier(entity: Entity) = 2f
            }

            val pos = Vec3.atCenterOf(blockPos.above())

            world.explode(
                projectile,
                null,
                behaviour,
                pos.x,
                pos.y,
                pos.z,
                3f,
                false,
                Level.ExplosionInteraction.BLOCK,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                ExplosionUtil.EXPLOSION_BLOCK_PARTICLES,
                SoundEvents.GENERIC_EXPLODE
            )
        }
    }
}
