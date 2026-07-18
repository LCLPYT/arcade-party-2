package work.lclpnet.ap2.impl.actor

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.objects.ObjectArraySet
import lombok.Getter
import lombok.Setter
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.Attributes
import work.lclpnet.ap2.api.actor.ActorData
import work.lclpnet.ap2.api.actor.ActorInit
import work.lclpnet.ap2.api.actor.BaseActor
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.impl.util.CodecUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShapes
import work.lclpnet.gaco.collisions.movement.MovementObserver
import work.lclpnet.kibu.access.entity.EntityUtil
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.player.PlayerJumpCallback
import java.util.*
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs

class GravityFieldActor(init: ActorInit, data: Data) : BaseActor(init) {
    private var observer: MovementObserver? = null
    private var manipulator: Manipulator? = null
    private var jumpCallback: PlayerJumpCallback? = null
    private var hooks: HookRegistrar? = null

    val shape: BlockShape = data.shape

    private var strength = data.strength

    fun enable(observer: MovementObserver, manipulator: Manipulator, hooks: HookRegistrar) {
        this.observer = observer
        this.manipulator = manipulator
        this.hooks = hooks

        observer.whenEntering(shape, ::onEnterField)
        observer.whenLeaving(shape, ::onLeaveField)

        jumpCallback = PlayerJumpCallback { player ->
            if (player.level() === world && shape.contains(player.x, player.y, player.z)) {
                onPlayerJumped(player)
            }

            false
        }

        PlayerJumpCallback.HOOK.registerWith(hooks, jumpCallback)
    }

    override fun onRemove() {
        super.onRemove()

        observer?.removeListeners(shape)

        if (jumpCallback != null) {
            hooks?.unregisterHook(PlayerJumpCallback.HOOK, jumpCallback)
        }
    }

    private fun onPlayerJumped(player: ServerPlayer) {
        if (strength > Attributes.GRAVITY.value().defaultValue) return

        val gravity = player.getAttributeBaseValue(Attributes.GRAVITY)

        if (abs(gravity - strength) > 1e-5) return

        player.level().sendParticles(
            ParticleTypes.CLOUD,
            player.x,
            player.y + 0.25,
            player.z,
            10,
            0.2,
            0.2,
            0.2,
            0.1
        )
    }

    private fun onEnterField(player: ServerPlayer) {
        if (manipulator != null && player.level() === world) {
            val change = manipulator!!.add(player, this)

            onGravityChanged(player, change)
        }
    }

    private fun onLeaveField(player: ServerPlayer) {
        if (manipulator != null && player.level() === world) {
            val change = manipulator!!.remove(player, this)

            onGravityChanged(player, change)
        }
    }

    fun onGravityChanged(player: ServerPlayer, gravityDelta: Double) {
        if (gravityDelta < 0) {
            player.playNotifySound(
                SoundEvents.BREEZE_IDLE_GROUND,
                SoundSource.PLAYERS,
                0.55f,
                1.5f
            )

            player.level().sendParticles(
                ParticleTypes.CLOUD,
                player.x,
                player.y,
                player.z,
                10,
                0.2,
                0.2,
                0.2,
                0.1
            )
        } else if (gravityDelta > 0) {
            player.playNotifySound(
                SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS,
                0.35f,
                0.75f
            )
        }
    }

    override fun createData(): ActorData<*> {
        return ActorData(Data(shape, strength), Data.CODEC)
    }

    data class Data(
        val shape: BlockShape,
        val strength: Double
    ) {
        companion object {
            val CODEC: Codec<Data> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockShapes.CODEC.fieldOf("shape").forGetter(Data::shape),
                    CodecUtil.FINITE_DOUBLE.fieldOf("strength").forGetter(Data::strength)
                ).apply(instance) { shape, strength ->
                    Data(shape, strength)
                }
            }
        }
    }

    class Manipulator {
        private val entries = WeakHashMap<ServerPlayer, Entry>()

        @Synchronized
        fun add(player: ServerPlayer, field: GravityFieldActor): Double {
            val entry = entries.computeIfAbsent(player) { p ->
                Entry(
                    ObjectArraySet(1),
                    p.getAttributeBaseValue(Attributes.GRAVITY)
                )
            }

            if (!entry.fields.add(field)) {
                return 0.0
            }

            return update(player, entry)
        }

        @Synchronized
        fun remove(player: ServerPlayer, field: GravityFieldActor): Double {
            val entry = entries.getOrDefault(player, null)

            if (entry == null || !entry.fields.remove(field)) {
                return 0.0
            }

            val change = update(player, entry)

            if (entry.fields.isEmpty()) {
                entries.remove(player)
            }

            return change
        }

        fun update(player: ServerPlayer, entry: Entry): Double {
            val strength = entry.fields.stream()
                .mapToDouble { it.strength }
                .max()
                .orElse(entry.initialStrength)

            val prevTotalGravity = player.getAttributeValue(Attributes.GRAVITY)

            EntityUtil.setAttribute(player, Attributes.GRAVITY, strength)

            return player.getAttributeValue(Attributes.GRAVITY) - prevTotalGravity
        }

        data class Entry(
            val fields: MutableSet<GravityFieldActor>,
            val initialStrength: Double
        )
    }
}
