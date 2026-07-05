package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.core.RegistryAccess
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.paintball.util.InkSettings
import work.lclpnet.ap2.game.paintball.util.NO_TRAIL
import work.lclpnet.ap2.game.paintball.util.PaintGun
import work.lclpnet.ap2.game.paintball.util.PaintGunManager
import work.lclpnet.ap2.impl.util.ItemHelper

const val SHOTGUN_ID = "shotgun"

class ShotgunKit(handle: KitHandle, paintGunManager: PaintGunManager) : PaintGunKit(
    handle = handle,
    id = SHOTGUN_ID,
    item = Items.LEATHER_HORSE_ARMOR,
    count = 1,
    paintGun = shotgunGun(),
    paintGunManager = paintGunManager
) {

    override fun createItemStack(manager: RegistryAccess): ItemStack {
        val stack = ItemHelper.getLeatherArmor(item, 0x1fd122)
        configureItemStack(stack)
        return stack
    }
}

private fun shotgunGun() = PaintGun(
    id = SHOTGUN_ID,
    cooldownTicks = 26,
    bulletCount = 2,
    bulletSpread = 9.0,
    ammo = 14,
    reloadTicks = 14,
    reloadAmount = 3,
    fireSound = PaintGun.SoundCfg(sound = SoundEvents.CHICKEN_EGG, volume = 0.3f, pitch = 0.5f),
    ink = InkSettings(
        speed = 20.0,
        gravity = 13.0,
        range = 13.0,
        blobCount = 12,
        blobRadius = 0.15,
        blobSpread = Math.toRadians(15.0),
        splatRadius = 1.5f,
        damage = 1f,
        deficitPaintBoost = 0.1f,
        trail = NO_TRAIL
    )
)
