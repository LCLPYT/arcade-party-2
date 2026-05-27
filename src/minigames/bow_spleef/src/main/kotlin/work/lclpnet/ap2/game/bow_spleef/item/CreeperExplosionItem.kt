package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth.lerp
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.level.Level
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.world.ExplosionUtil
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*

private const val DURATION_TICKS = 25

class CreeperExplosionItem : SpecialItem {

    private val tasks = mutableMapOf<UUID, TaskHandle>()

    override fun id(): String = "creeper_explosion"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.CREEPER_HEAD)

    override fun canBeDropped(player: ServerPlayer, stack: ItemStack): Boolean = !tasks.containsKey(player.uuid)

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        if (tasks.containsKey(player.uuid)) return InteractionResult.FAIL

        tasks[player.uuid] = ctx.scheduler().interval(1, object : SchedulerAction {
            var t = 0

            override fun run(task: RunningTask) {
                if (player.hasDisconnected()) {
                    task.cancel()
                    return
                }

                val pitch = lerp(t.toFloat() / DURATION_TICKS, 0.85f, 1.45f)

                val world: ServerLevel = player.level()
                world.playSound(null, player.x, player.eyeY, player.z, SoundEvents.CREEPER_HURT, SoundSource.HOSTILE, 0.2f, pitch)
                world.sendParticles(ParticleTypes.FLAME, player.x, player.y, player.z, 10, 0.1, 0.1, 0.1, 0.15)

                if (t++ < DURATION_TICKS) return

                task.cancel()
                ctx.removeSpecialItem(player, this@CreeperExplosionItem)

                val behaviour = object : ExplosionDamageCalculator() {
                    override fun getKnockbackMultiplier(entity: Entity) = 2.5f
                }

                world.explode(
                    player,
                    null,
                    behaviour,
                    player.x,
                    player.y,
                    player.z,
                    3.5f,
                    false,
                    Level.ExplosionInteraction.BLOCK,
                    ParticleTypes.EXPLOSION,
                    ParticleTypes.EXPLOSION_EMITTER,
                    ExplosionUtil.EXPLOSION_BLOCK_PARTICLES,
                    SoundEvents.GENERIC_EXPLODE
                )

                world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.x, player.y, player.z, 1, 0.1, 0.1, 0.1, 0.15)
            }
        }).whenComplete {
            tasks.remove(player.uuid)
        }

        player.cooldowns.addCooldown(stack, DURATION_TICKS)
        PlayerUtils.syncPlayerItems(player)

        return InteractionResult.SUCCESS_SERVER
    }
}
