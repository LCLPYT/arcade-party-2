package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface RangedWeaponUsedCallback {

    Hook<RangedWeaponUsedCallback> HOOK = HookFactory.createArrayBacked(RangedWeaponUsedCallback.class, hooks -> (entity, stack, remainingUseTicks) -> {
        for (RangedWeaponUsedCallback hook : hooks) {
            hook.onShot(entity, stack, remainingUseTicks);
        }
    });

    void onShot(LivingEntity entity, ItemStack stack, int remainingUseTicks);
}
