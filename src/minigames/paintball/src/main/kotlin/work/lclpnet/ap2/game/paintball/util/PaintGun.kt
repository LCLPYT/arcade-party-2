package work.lclpnet.ap2.game.paintball.util

import net.minecraft.sounds.SoundEvent

val NO_SPLIT = PaintGun.BulletSplit(
    splitTicks = Int.MAX_VALUE,
    maxSplits = 0,
    splitPaintRadius = 0f,
    splitSubdivisions = 0
)

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
    data class BulletSettings(
        val size: Double,
        val power: Double,
        val maxHits: Double,
        val despawnSeconds: Double,
        val mass: Float,
        val damage: Float,
        val maxImpactPower: Float,
        val paintRadius: Float,
        val deficitPaintBoost: Float,
        val split: BulletSplit
    )

    data class BulletSplit(
        val splitTicks: Int,
        val maxSplits: Int,
        val splitPaintRadius: Float,
        val splitSubdivisions: Int
    )

    data class SoundCfg(val sound: SoundEvent, val volume: Float, val pitch: Float)
}
