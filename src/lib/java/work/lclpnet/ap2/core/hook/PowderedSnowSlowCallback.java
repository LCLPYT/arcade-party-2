package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PowderedSnowSlowCallback {

    Hook<PowderedSnowSlowCallback> ADD = HookFactory.createArrayBacked(PowderedSnowSlowCallback.class, hooks -> (entity) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.shouldCancel(entity)) {
                cancel = true;
            }
        }

        return cancel;
    });

    Hook<PowderedSnowSlowCallback> REMOVE = HookFactory.createArrayBacked(PowderedSnowSlowCallback.class, hooks -> (entity) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.shouldCancel(entity)) {
                cancel = true;
            }
        }

        return cancel;
    });


    boolean shouldCancel(LivingEntity entity);
}
