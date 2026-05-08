package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.core.hook.ExplosionAffectedEntitiesCallback;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.KitOptions;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;

import java.util.List;

public class WindChargeKit extends SingleItemKit {

    public static final String ID = "wind_charge";

    private static final int CHARGES = 4;

    public WindChargeKit(KitHandle handle) {
        super(handle, ID, Items.WIND_CHARGE, CHARGES);
    }

    @Override
    public void init(KitOptions options) {
        ExplosionAffectedEntitiesCallback.HOOK.registerWith(handle.hooks(), (explosion, affected) -> {
            if (explosion.getDirectSourceEntity() instanceof WindCharge) {
                LivingEntity owner = explosion.getIndirectSourceEntity();

                return owner != null ? List.of(owner) : List.of();
            }

            return affected;
        });
    }
}
