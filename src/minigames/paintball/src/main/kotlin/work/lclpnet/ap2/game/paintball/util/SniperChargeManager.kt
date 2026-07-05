package work.lclpnet.ap2.game.paintball.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.ap2.game.paintball.kit.SNIPER_ID
import java.util.*
import kotlin.math.roundToInt

private const val CHARGE_TICKS_TO_FULL = 35
private const val MIN_FIRE_CHARGE = 0.3

private const val MIN_RANGE = 10.0
private const val MIN_DAMAGE = 8.0f
private const val MIN_DROPLET_RADIUS = 0.9f

/**
 * Drives the charge based sniper.
 *
 * While a player holds the sniper, a spyglass is kept in their offhand so they can scope in.
 * Holding right click uses that spyglass, which is polled every tick to build up the charge.
 * Releasing right click fires a shot whose reach and damage scale with the accumulated charge.
 */
class SniperChargeManager(private val paintGunManager: PaintGunManager) {

    private val chargeTicks = Object2IntOpenHashMap<UUID>()

    fun tick(player: ServerPlayer) {
        if (!isSniperSelected(player)) {
            reset(player)
            clearOffhand(player)
            return
        }

        println("${player.scoreboardName}dd diving = ${paintGunManager.isReloading(player)}")
        val weaponStack = player.mainHandItem
        val blocked = player.cooldowns.isOnCooldown(weaponStack) || paintGunManager.isReloading(player)

        if (paintGunManager.hasAmmo(player) && !blocked) {
            ensureSpyglass(player)
        } else {
            // no ammo = no scoping
            clearOffhand(player)
        }

        val charging = player.isUsingItem && player.useItem.isOf(Items.SPYGLASS)

        if (charging) {
            val ticks = (chargeTicks.getInt(player.uuid) + 1).coerceAtMost(CHARGE_TICKS_TO_FULL)
            chargeTicks.put(player.uuid, ticks)
            sendChargeOverlay(player, ticks.toDouble() / CHARGE_TICKS_TO_FULL)
        } else if (chargeTicks.getInt(player.uuid) > 0) {
            onRelease(player)
        }
    }

    /**
     * Clears the charge and closes the scope. Called when the player dives, dies or switches kits.
     */
    fun reset(player: ServerPlayer) {
        clearCharge(player)

        if (player.isUsingItem && player.useItem.isOf(Items.SPYGLASS)) {
            player.stopUsingItem()
        }
    }

    private fun onRelease(player: ServerPlayer) {
        val ticks = chargeTicks.getInt(player.uuid)
        clearCharge(player)

        val fraction = (ticks.toDouble() / CHARGE_TICKS_TO_FULL).coerceIn(0.0, 1.0)

        if (fraction < MIN_FIRE_CHARGE) return

        val pair = paintGunManager.getPaintGunAndStack(player).orElse(null) ?: return
        val gun = pair.left()
        val stack = pair.right()

        paintGunManager.shootCharged(player, gun, stack, scaleInk(gun.ink, fraction))
    }

    private fun clearCharge(player: ServerPlayer) {
        val had = chargeTicks.removeInt(player.uuid)

        if (had > 0) {
            player.sendOverlayMessage(Component.empty())
        }
    }

    private fun isSniperSelected(player: ServerPlayer): Boolean {
        if (player.isSpectator || !player.isAlive) return false

        return SingleItemKit.getId(player.mainHandItem) == SNIPER_ID
    }

    private fun ensureSpyglass(player: ServerPlayer) {
        if (!player.offhandItem.isOf(Items.SPYGLASS)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(Items.SPYGLASS))
        }
    }

    private fun clearOffhand(player: ServerPlayer) {
        if (player.offhandItem.isOf(Items.SPYGLASS)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    private fun sendChargeOverlay(player: ServerPlayer, fraction: Double) {
        val percent = (fraction * 100).roundToInt()

        val color = when {
            fraction >= 1.0 -> ChatFormatting.GREEN
            fraction >= MIN_FIRE_CHARGE -> ChatFormatting.YELLOW
            else -> ChatFormatting.GRAY
        }

        player.sendOverlayMessage(Component.literal("$percent%").withStyle(color))
    }

    /**
     * Scales the full charge ink settings down according to the charge [fraction] in `[0, 1]`.
     * The [base] settings define the values at full charge.
     */
    private fun scaleInk(base: InkSettings, fraction: Double): InkSettings = base.copy(
        range = lerp(MIN_RANGE, base.range, fraction),
        damage = lerp(MIN_DAMAGE, base.damage, fraction),
        trail = base.trail.copy(
            dropletRadius = lerp(MIN_DROPLET_RADIUS, base.trail.dropletRadius, fraction),
            subdivisions = if (fraction > 0.6) base.trail.subdivisions else 2
        )
    )

    private fun lerp(a: Double, b: Double, fraction: Double): Double = a + (b - a) * fraction

    private fun lerp(a: Float, b: Float, fraction: Double): Float = a + (b - a) * fraction.toFloat()
}
