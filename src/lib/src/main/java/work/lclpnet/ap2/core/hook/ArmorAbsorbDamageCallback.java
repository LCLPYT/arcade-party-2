package work.lclpnet.ap2.core.hook;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ArmorAbsorbDamageCallback {

    Hook<ArmorAbsorbDamageCallback> HOOK = HookFactory.createArrayBacked(ArmorAbsorbDamageCallback.class, hooks -> (victim, source, damage) -> {
        boolean allow = true;

        for (var hook : hooks) {
            if (!hook.mayAbsorbDamage(victim, source, damage)) {
                allow = false;
            }
        }

        return allow;
    });

    boolean mayAbsorbDamage(LivingEntity victim, DamageSource source, float damage);
}
