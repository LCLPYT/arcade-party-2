package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.paintball.util.PaintGun
import work.lclpnet.ap2.game.paintball.util.PaintGunManager

const val SNIPER_ID = "sniper"

class SniperKit(handle: KitHandle, paintGunManager: PaintGunManager) : PaintGunKit(
    handle = handle,
    id = SNIPER_ID,
    item = Items.DIAMOND_HORSE_ARMOR,
    count = 1,
    paintGun = sniperGun(),
    paintGunManager = paintGunManager
)

private fun sniperGun() = PaintGun(
    id = SNIPER_ID,
    cooldownTicks = 28,
    bulletCount = 1,
    bulletSpread = 0.0,
    ammo = 8,
    reloadTicks = 17,
    reloadAmount = 2,
    fireSound = PaintGun.SoundCfg(sound = SoundEvents.MACE_SMASH_AIR, volume = 0.4f, pitch = 1f),
    bullet = PaintGun.BulletSettings(
        size = 0.3,
        power = 100.0,
        maxHits = 1.0,
        despawnSeconds = 1.0,
        mass = 0.01f,
        damage = 19.5f,
        maxImpactPower = 4f,
        restitution = 0.2f,
        angularDamping = 0.1f,
        paintRadius = 2.9f,
        deficitPaintBoost = 0.1f,
        split = PaintGun.BulletSplit(
            splitTicks = 0,
            maxSplits = 14,
            splitPaintRadius = 1.48f,
            splitSubdivisions = 1
        )
    )
)
