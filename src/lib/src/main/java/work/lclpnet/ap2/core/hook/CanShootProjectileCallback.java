package work.lclpnet.ap2.core.hook;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface CanShootProjectileCallback {

    Hook<CanShootProjectileCallback> HOOK = HookFactory.createArrayBacked(CanShootProjectileCallback.class, hooks -> (shooter, stack, hand) -> {
        boolean allow = true;

        for (var hook : hooks) {
            if (!hook.canShoot(shooter, stack, hand)) {
                allow = false;
            }
        }

        return allow;
    });

    boolean canShoot(LivingEntity shooter, ItemStack stack, InteractionHand hand);
}
