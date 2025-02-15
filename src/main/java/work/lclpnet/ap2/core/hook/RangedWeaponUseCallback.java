package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface RangedWeaponUseCallback {

    Hook<RangedWeaponUseCallback> HOOK = HookFactory.createArrayBacked(RangedWeaponUseCallback.class, hooks -> (entity, stack) -> {
        for (RangedWeaponUseCallback hook : hooks) {
            hook.onShoot(entity, stack);
        }
    });

    void onShoot(LivingEntity entity, ItemStack stack);
}
