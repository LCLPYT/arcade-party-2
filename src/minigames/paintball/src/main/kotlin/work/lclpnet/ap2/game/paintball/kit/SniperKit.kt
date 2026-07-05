package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.paintball.util.InkSettings
import work.lclpnet.ap2.game.paintball.util.InkTrail
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
    ink = InkSettings(
        speed = 70.0,
        gravity = 1.5,
        range = 45.0,
        blobCount = 3,
        blobRadius = 0.22,
        blobSpread = Math.toRadians(0.5),
        splatRadius = 1.6f,
        damage = 19.5f,
        deficitPaintBoost = 0.1f,
        trail = InkTrail(
            trailTicks = 1,
            maxDroplets = 30,
            dropletRadius = 1.1f,
            subdivisions = 1
        )
    )
)
