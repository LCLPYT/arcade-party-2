package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.paintball.util.InkSettings
import work.lclpnet.ap2.game.paintball.util.InkTrail
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
    ink = InkSettings(
        speed = 28.0,
        gravity = 10.0,
        range = 14.0,
        blobCount = 4,
        blobRadius = 0.18,
        blobSpread = Math.toRadians(2.0),
        splatRadius = 1.4f,
        damage = 1.4f,
        deficitPaintBoost = 0.75f,
        trail = InkTrail(
            trailTicks = 3,
            maxDroplets = 4,
            dropletRadius = 1.0f,
            subdivisions = 0
        )
    )
)
