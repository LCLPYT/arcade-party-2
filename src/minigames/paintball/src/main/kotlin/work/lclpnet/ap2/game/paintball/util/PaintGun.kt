package work.lclpnet.ap2.game.paintball.util

import net.minecraft.sounds.SoundEvent

/**
 * @param id The id of the paint gun.
 * @param cooldownTicks The fire cooldown of the paint gun, in ticks.
 * @param bulletCount How many ink clusters this gun fires per shot.
 * @param bulletSpread The maximum spread of this gun, in degrees deviating from the looking direction.
 * @param ammo How much ammo this gun has.
 * @param reloadTicks How many ticks it takes for the player to reload one ammo unit with this gun.
 * @param reloadAmount How much ammo should be reloaded per ammo unit (per reload tick).
 * @param fireSound The fire sound.
 * @param ink The ink settings.
 */
data class PaintGun(
    val id: String,
    val cooldownTicks: Int,
    val bulletCount: Int,
    val bulletSpread: Double,
    val ammo: Int,
    val reloadTicks: Int,
    val reloadAmount: Int,
    val fireSound: SoundCfg,
    val ink: InkSettings
) {
    data class SoundCfg(val sound: SoundEvent, val volume: Float, val pitch: Float)
}
