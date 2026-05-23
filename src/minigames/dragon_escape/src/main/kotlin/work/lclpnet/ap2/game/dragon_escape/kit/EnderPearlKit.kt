package work.lclpnet.ap2.game.dragon_escape.kit

import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting.RED
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.hook.EnderPearlTeleportCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.impl.game.kit.KitHandle
import work.lclpnet.ap2.impl.game.kit.KitOptions
import work.lclpnet.ap2.impl.game.kit.SingleItemKit
import work.lclpnet.gaco.math.SplinePath
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*

private const val ID = "ender_pearl"
private val ORIGIN_CODEC: MapCodec<Vec3> = Vec3.CODEC.fieldOf("ap2:origin")
private const val MAX_PROGRESS_SKIP = 0.2
private val RETRY_TICKS = Ticks.seconds(2)
private val REFUND_DELAY_TICKS = Ticks.seconds(10)

class EnderPearlKit(
    handle: KitHandle,
    private val path: SplinePath
) : SingleItemKit(handle, ID, Items.ENDER_PEARL, 1) {

    private val refundTasks = HashMap<UUID, TaskHandle>()

    override fun init(options: KitOptions) {
        ProjectileShootCallback.HOOK.registerWith(handle.hooks()) { shooter, projectile ->
            if (shooter is ServerPlayer && projectile is ThrownEnderpearl && handle.hasKitEquipped(shooter, this)) {
                CustomNbt.set(projectile, ORIGIN_CODEC, shooter.position())

                val uuid = projectile.uuid

                refundTasks[uuid] = handle.scheduler().timeout(REFUND_DELAY_TICKS) { ->
                    refundTasks.remove(uuid)
                    projectile.discard()
                    refund(shooter.connection, options)
                }
            }
        }

        EnderPearlTeleportCallback.HOOK.registerWith(handle.hooks()) { owner, enderPearl, pos ->
            if (owner is ServerPlayer && handle.hasKitEquipped(owner, this)) {
                val refundTask = refundTasks.remove(enderPearl.uuid)
                refundTask?.cancel()

                if (canTeleportTo(enderPearl, pos)) return@registerWith false

                enderPearl.discard()

                handle.translations().translateText("game.ap2.dragon_escape.teleport_too_far")
                    .formatted(RED)
                    .sendTo(owner)

                refund(owner.connection, options)

                owner.cooldowns.addCooldown(BuiltInRegistries.ITEM.getKey(Items.ENDER_PEARL), RETRY_TICKS)

                return@registerWith true
            }

            enderPearl.discard()
            true
        }
    }

    private fun canTeleportTo(enderPearl: ThrownEnderpearl, target: Vec3): Boolean {
        val origin = CustomNbt.get(enderPearl, ORIGIN_CODEC).orElse(null) ?: return false

        val progressFrom = path.getProgress(origin)
        val progressTo = path.getProgress(target)

        return progressTo - progressFrom <= MAX_PROGRESS_SKIP
    }

    private fun refund(handler: ServerGamePacketListenerImpl, options: KitOptions) {
        if (!handler.isAcceptingMessages) return

        val player = handler.player

        if (player.isDeadOrDying) return

        equip(player, options)

        ServerPlayerAccess.playSoundToPlayer(
            player,
            SoundEvents.NOTE_BLOCK_BASS.value(),
            SoundSource.NEUTRAL,
            0.5f,
            1f
        )
    }
}
