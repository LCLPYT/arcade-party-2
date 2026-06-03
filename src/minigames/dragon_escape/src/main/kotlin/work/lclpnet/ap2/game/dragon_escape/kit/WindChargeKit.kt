package work.lclpnet.ap2.game.dragon_escape.kit

import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge
import net.minecraft.world.item.Items
import work.lclpnet.ap2.core.hook.ExplosionAffectedEntitiesCallback
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions
import work.lclpnet.ap2.game.kit.SingleItemKit

private const val ID = "wind_charge"
private const val CHARGES = 4

class WindChargeKit(handle: KitHandle) : SingleItemKit(handle, ID, Items.WIND_CHARGE, CHARGES) {

    override fun init(options: KitOptions) {
        ExplosionAffectedEntitiesCallback.HOOK.registerWith(handle.hooks) { explosion, affected ->
            if (explosion.directSourceEntity is WindCharge) {
                val owner = explosion.indirectSourceEntity

                return@registerWith if (owner != null) listOf(owner) else emptyList()
            }

            affected
        }
    }
}
