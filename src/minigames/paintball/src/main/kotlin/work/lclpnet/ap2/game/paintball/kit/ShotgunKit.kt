package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.core.RegistryAccess
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.kit.KitHandle
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
    bulletCount = 7,
    bulletSpread = 9.0,
    ammo = 14,
    reloadTicks = 14,
    reloadAmount = 3,
    fireSound = PaintGun.SoundCfg(sound = SoundEvents.CHICKEN_EGG, volume = 0.3f, pitch = 0.5f),
    bullet = PaintGun.BulletSettings(
        size = 0.15,
        power = 25.0,
        maxHits = 16.0,
        despawnSeconds = 1.5,
        mass = 0.05f,
        damage = 4f,
        maxImpactPower = 5f,
        restitution = 0.2f,
        angularDamping = 0.2f,
        paintRadius = 2.0f,
        deficitPaintBoost = 0.1f,
        split = PaintGun.BulletSplit(
            splitTicks = 10,
            maxSplits = 2,
            splitPaintRadius = 1.3f,
            splitSubdivisions = 0
        )
    )
)
