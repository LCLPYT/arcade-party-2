package work.lclpnet.ap2.game.paintball.util

import net.minecraft.sounds.SoundEvent

val NO_SPLIT = PaintGun.BulletSplit(
    splitTicks = Int.MAX_VALUE,
    maxSplits = 0,
    splitPaintRadius = 0f,
    splitSubdivisions = 0
)

/**
 * @param id The id of the paint gun.
 * @param cooldownTicks The fire cooldown of the paint gun, in ticks.
 * @param bulletCount How many bullets this gun fires per shot.
 * @param bulletSpread The maximum bullet spread of this gun, in degrees deviating from the looking direction.
 * @param ammo How much ammo this gun has.
 * @param reloadTicks How many ticks it takes for the player to reload one ammo unit with this gun.
 * @param reloadAmount How much ammo should be reloaded per ammo unit (per reload tick).
 * @param fireSound The fire sound.
 * @param bullet The bullet settings.
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
    val bullet: BulletSettings
) {
    /**
     * @param size How big bullets of this gun should be in the world. Is applied as a scale multiplier.
     * @param power The power with which to shoot the bullet.
     * @param maxHits How much hit events an individual bullet can have before it despawns.
     * @param despawnSeconds How long until the bullet despawns after it hits something.
     * @param mass The mass of the bullet.
     * @param damage How much damage a hit of this bullet should deal when hitting an entity.
     * @param maxImpactPower Limit the velocity of this bullet upon impact to this maximum value.
     * @param restitution The bounciness of the bullet (0 = no bounce, 1 = perfectly elastic).
     * @param angularDamping Fraction (0..1) that bleeds off the bullet's spin so it settles like a die.
     * @param paintRadius How big the affected block paint radius should be (in blocks).
     * @param deficitPaintBoost How much the paint radius should be boosted per missing player in a team (multiplier).
     * @param split The bullet split configuration.
     */
    data class BulletSettings(
        val size: Double,
        val power: Double,
        val maxHits: Double,
        val despawnSeconds: Double,
        val mass: Float,
        val damage: Float,
        val maxImpactPower: Float,
        val restitution: Float,
        val angularDamping: Float,
        val paintRadius: Float,
        val deficitPaintBoost: Float,
        val split: BulletSplit
    )

    /**
     * When shooting a bullet in the air, the ink bullet should drop some ink droplets in the flight path of the ink bullet.
     * @param splitTicks The ticks after which a paintball bullet leaves a droplet.
     * @param maxSplits The maximum number of droplets that can be left by a single bullet.
     * @param splitPaintRadius The affected block paint radius of the droplets.
     * @param splitSubdivisions For very fast projectiles, the droplet leaving process doesn't really work.
     * Configuring subdivisions splits the flight path into the specified number of subdivisions and leaves a droplet at the boundary between subdivisions.
     */
    data class BulletSplit(
        val splitTicks: Int,
        val maxSplits: Int,
        val splitPaintRadius: Float,
        val splitSubdivisions: Int
    )

    data class SoundCfg(val sound: SoundEvent, val volume: Float, val pitch: Float)
}
