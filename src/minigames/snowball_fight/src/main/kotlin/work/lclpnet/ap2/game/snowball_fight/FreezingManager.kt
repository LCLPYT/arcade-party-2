package work.lclpnet.ap2.game.snowball_fight

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
import net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH
import net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.core.hook.FrozenTickChangeCallback
import work.lclpnet.ap2.core.hook.PowderedSnowSlowCallback
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.resetAttribute
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.world.CombatIdleManager
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import java.util.*
import kotlin.math.roundToInt

private val POWDER_SNOW_CANCEL_MODIFIER_ID: Identifier = ApConstants.identifier("powder_snow_cancel")

class FreezingManager(
    private val scheduler: TaskScheduler,
    private val translations: Translations,
    private val participants: Participants,
    private val freezingStartTicks: Int,
    freezingTicks: Int
) {
    private val freezingTicks = maxOf(1, freezingTicks)
    private val tasks = HashMap<UUID, TaskHandle>()

    fun enable(hooks: HookRegistrar) {
        val idleManager = CombatIdleManager(participants, freezingStartTicks)

        idleManager.onEnterIdle().register { player ->
            translations.translateText("game.ap2.snowball_fight.idle")
                .formatted(ChatFormatting.YELLOW)
                .sendTo(player)

            player.level().sendParticles(ParticleTypes.SNOWFLAKE, player.x, player.y + 1, player.z, 50, 0.5, 1.0, 0.5, 0.1)

            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.5f)
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.25f, 0.8f)
            player.playNotifySound(SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.2f, 1.8f)

            startFreezing(player)
        }

        idleManager.onLeaveIdle().register(::stopFreezing)
        idleManager.enable(scheduler, hooks)

        FrozenTickChangeCallback.HOOK.registerWith(hooks) { entity, ticks ->
            entity is ServerPlayer
                && ticks <= entity.ticksFrozen
                && participants.isParticipating(entity)
                && entity.uuid in tasks
        }

        PowderedSnowSlowCallback.ADD.registerWith(hooks) { entity ->
            if (entity !is ServerPlayer || !participants.isParticipating(entity) || entity.uuid !in tasks) return@registerWith false

            val instance = entity.getAttribute(MOVEMENT_SPEED) ?: return@registerWith false

            if (!instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) return@registerWith false

            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)
            false
        }

        PowderedSnowSlowCallback.REMOVE.registerWith(hooks) { entity ->
            if (entity !is ServerPlayer || !participants.isParticipating(entity) || entity.uuid !in tasks || entity.ticksFrozen <= 0) return@registerWith false

            val instance = entity.getAttribute(MOVEMENT_SPEED) ?: return@registerWith false
            val cancellationFactor = -0.05f * entity.percentFrozen

            instance.addTransientModifier(AttributeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID, cancellationFactor.toDouble(), ADD_VALUE))
            false
        }
    }

    fun startFreezing(player: ServerPlayer) {
        player.setAttribute(JUMP_STRENGTH, 0.0)

        var time = 0
        val prevTask = tasks.put(player.uuid, scheduler.interval({ task ->
            if (player.hasDisconnected() || !player.isAlive) {
                task.cancel()
                return@interval
            }
            val t = time++
            val progress = (t.toDouble() / freezingTicks).coerceIn(0.0, 1.0)
            val frozenTicks = (player.ticksRequiredToFreeze * progress).roundToInt()

            player.ticksFrozen = frozenTicks

            if (t >= freezingTicks) {
                task.cancel()
            }
        }, 1))

        prevTask?.cancel()
    }

    fun stopFreezing(player: ServerPlayer) {
        val task = tasks.remove(player.uuid) ?: return
        task.cancel()

        player.ticksFrozen = 0
        player.resetAttribute(JUMP_STRENGTH)

        val instance = player.getAttribute(MOVEMENT_SPEED)

        if (instance != null && instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) {
            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)
        }
    }
}
