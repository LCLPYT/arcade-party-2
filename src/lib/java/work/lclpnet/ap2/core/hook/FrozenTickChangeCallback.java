package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface FrozenTickChangeCallback {

    Hook<FrozenTickChangeCallback> HOOK = HookFactory.createArrayBacked(FrozenTickChangeCallback.class, hooks -> (entity, ticks) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onFrozenTicksChange(entity, ticks)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onFrozenTicksChange(Entity entity, int ticks);
}
