package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
