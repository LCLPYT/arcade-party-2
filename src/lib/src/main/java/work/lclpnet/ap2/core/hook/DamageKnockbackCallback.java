package work.lclpnet.ap2.core.hook;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface DamageKnockbackCallback {

    Hook<DamageKnockbackCallback> HOOK = HookFactory.createArrayBacked(DamageKnockbackCallback.class, hooks -> (affected, source, damage) -> {
        boolean allow = true;

        for (var hook : hooks) {
            if (!hook.shouldTakeKnockback(affected, source, damage)) {
                allow = false;
            }
        }

        return allow;
    });

    boolean shouldTakeKnockback(LivingEntity affected, DamageSource source, float damage);
}
