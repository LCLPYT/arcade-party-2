package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.paintball.util.PaintGun
import work.lclpnet.ap2.game.paintball.util.PaintGunManager

const val RIFLE_ID = "rifle"

class RifleKit(handle: KitHandle, paintGunManager: PaintGunManager) : PaintGunKit(
    handle = handle,
    id = RIFLE_ID,
    item = Items.IRON_HORSE_ARMOR,
    count = 1,
    paintGun = rifleGun(),
    paintGunManager = paintGunManager
)

private fun rifleGun() = PaintGun(
    id = RIFLE_ID,
    cooldownTicks = 3,
    bulletCount = 1,
    bulletSpread = 0.5,
    ammo = 70,
    reloadTicks = 2,
    reloadAmount = 4,
    fireSound = PaintGun.SoundCfg(sound = SoundEvents.ITEM_PICKUP, volume = 0.2f, pitch = 2f),
    bullet = PaintGun.BulletSettings(
        size = 0.2,
        power = 25.0,
        maxHits = 12.0,
        despawnSeconds = 2.0,
        mass = 0.1f,
        damage = 3.0f,
        maxImpactPower = 4f,
        restitution = 0.25f,
        angularDamping = 0.15f,
        paintRadius = 1.5f,
        deficitPaintBoost = 0.75f,
        split = PaintGun.BulletSplit(
            splitTicks = 4,
            maxSplits = 6,
            splitPaintRadius = 1.1f,
            splitSubdivisions = 0
        )
    )
)
