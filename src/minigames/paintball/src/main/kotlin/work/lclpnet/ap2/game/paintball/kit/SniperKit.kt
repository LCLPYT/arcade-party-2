package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions
import work.lclpnet.ap2.game.paintball.util.InkSettings
import work.lclpnet.ap2.game.paintball.util.InkTrail
import work.lclpnet.ap2.game.paintball.util.PaintGun
import work.lclpnet.ap2.game.paintball.util.PaintGunManager

const val SNIPER_ID = "sniper"

class SniperKit(handle: KitHandle, private val paintGunManager: PaintGunManager) : PaintGunKit(
    handle = handle,
    id = SNIPER_ID,
    item = Items.DIAMOND_HORSE_ARMOR,
    count = 1,
    paintGun = sniperGun(),
    paintGunManager = paintGunManager
) {

    override fun onUse(player: ServerPlayer, stack: ItemStack): InteractionResult {
        if (!paintGunManager.hasAmmo(player)) {
            paintGunManager.notifyNoAmmo(player)
        }

        return InteractionResult.PASS
    }

    override fun unequip(player: ServerPlayer, options: KitOptions) {
        super.unequip(player, options)

        paintGunManager.sniperCharge.reset(player)

        if (player.offhandItem.isOf(Items.SPYGLASS)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }
}

private fun sniperGun() = PaintGun(
    id = SNIPER_ID,
    cooldownTicks = 0,
    bulletCount = 1,
    bulletSpread = 0.0,
    ammo = 8,
    reloadTicks = 17,
    reloadAmount = 2,
    fireSound = PaintGun.SoundCfg(sound = SoundEvents.MACE_SMASH_AIR, volume = 0.4f, pitch = 1f),
    // These ink settings describe a fully charged shot. Partial charges are scaled down in SniperChargeManager.
    ink = InkSettings(
        speed = 200.0,
        gravity = 1.0,
        range = 48.0,
        blobCount = 1,
        blobRadius = 0.25,
        blobSpread = 0.0,
        splatRadius = 1.8f,
        damage = 20.0f,
        deficitPaintBoost = 0.1f,
        trail = InkTrail(
            trailTicks = 1,
            maxDroplets = 60,
            dropletRadius = 1.6f,
            subdivisions = 4
        )
    )
)
