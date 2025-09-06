package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.Items;
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
        handle.hooks().registerHook(ExplosionAffectedEntitiesCallback.HOOK, (explosion, affected) -> {
            if (explosion.getEntity() instanceof WindChargeEntity) {
                LivingEntity owner = explosion.getCausingEntity();

                return owner != null ? List.of(owner) : List.of();
            }

            return affected;
        });
    }
}
