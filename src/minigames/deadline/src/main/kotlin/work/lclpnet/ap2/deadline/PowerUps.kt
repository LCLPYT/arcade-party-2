package work.lclpnet.ap2.deadline

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.Random
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val PICKUP_RADIUS = 1.0
private const val MARKER_SIZE = 0.5

private const val ITEM_SLOT = 4

// the interval of power up refreshes
private val REFRESH_INTERVAL = 30.seconds

private val COLLECT_SOUND = GameSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1f)
private val REFRESH_SOUND = GameSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f)

class PowerUps(private val gameHandle: MiniGameHandle, level: ServerLevel, private val random: Random) {

    private val types = listOf(SpeedBoost(), Jump(), Invisible())
    private val scene = Scene(ServerWorldMountContext(level))
    private val pickups = HashMap<BlockPos, BlockDisplayObject>()

    fun initHooks(cycles: (UUID) -> LightCycle?) {
        val participants = gameHandle.participants

        // Activate the power-up when the item is used
        PlayerInteractionHooks.USE_ITEM.registerWith(gameHandle.hooks) { player, _, hand ->
            if (player !is ServerPlayer || !participants.isParticipating(player)) {
                return@registerWith InteractionResult.PASS
            }

            val stack = player.getItemInHand(hand)
            val powerUp = types.firstOrNull { stack.isOf(it.item) }
            val cycle = cycles(player.uuid)

            if (powerUp == null || cycle == null || player.cooldowns.isOnCooldown(stack)) {
                return@registerWith InteractionResult.PASS
            }

            if (powerUp.duration > 0) {
                // the item stays in the slot with its cooldown sweep while the effect lasts, then disappears
                player.cooldowns.addCooldown(stack, powerUp.duration)
                gameHandle.scheduler.timeout(powerUp.duration) { -> stack.consume(1, player) }
            } else {
                stack.consume(1, player)
            }

            powerUp.activate(player, cycle)

            InteractionResult.SUCCESS_SERVER
        }

        // riders always have the power-up slot selected
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(gameHandle.hooks) { player, slot ->
            if (slot != ITEM_SLOT && participants.isParticipating(player)) {
                PlayerInventoryAccess.setSelectedSlot(player, ITEM_SLOT)
            }
        }

        for (player in participants) {
            PlayerInventoryAccess.setSelectedSlot(player, ITEM_SLOT)
        }
    }

    fun startRefreshing(positions: List<BlockPos>) {
        val subject = gameHandle.translations.translateText("power_up.refresh")

        val timer = BossBarTimer.builder(gameHandle.translations, subject)
            .withAlertSound(false)
            .withColor(BossEvent.BossBarColor.YELLOW)
            .withDurationTicks(REFRESH_INTERVAL.inWholeTicks.toInt())
            .build()

        timer.addPlayers(PlayerLookup.all(gameHandle.server))
        timer.start(gameHandle.bossBarProvider, gameHandle.scheduler)

        timer.whenDone {
            spawn(positions)

            for (player in gameHandle.participants) {
                REFRESH_SOUND.playTo(player)
            }

            startRefreshing(positions)
        }
    }

    fun spawn(positions: List<BlockPos>) {
        for (pos in positions) {
            if (pos in pickups) continue

            val marker = BlockDisplayObject(scene, Blocks.SEA_LANTERN.defaultBlockState())
            // center the shrunk marker inside its block position
            val margin = (1 - MARKER_SIZE) / 2
            marker.scale.set(MARKER_SIZE)
            marker.position.set(pos.x + margin, pos.y + margin, pos.z + margin)
            scene.add(marker)
            pickups[pos] = marker
        }
    }

    fun collect(rider: ServerPlayer, position: Vec3) {
        val pos = pickups.keys.firstOrNull {
            position.distanceToSqr(Vec3.atCenterOf(it)) <= PICKUP_RADIUS * PICKUP_RADIUS
        } ?: return

        pickups.remove(pos)?.detach()
        grant(rider, types[random.nextInt(types.size)])
    }

    private fun grant(rider: ServerPlayer, powerUp: PowerUp) {
        // new power up replaces the old one
        val stack = ItemStack(powerUp.item)
        val name = gameHandle.translations.translateText(rider, powerUp.nameKey).withStyle(ChatFormatting.GOLD)
        stack.set(DataComponents.ITEM_NAME, name)

        rider.inventory.setItem(ITEM_SLOT, stack)
        COLLECT_SOUND.playTo(rider)
    }
}
