package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface CobwebSlowCallback {

    Hook<CobwebSlowCallback> HOOK = HookFactory.createArrayBacked(CobwebSlowCallback.class, callbacks ->
            (entity, pos) -> {
                boolean cancel = false;

                for (var cb : callbacks) {
                    if (cb.cancelSlow(entity, pos)) {
                        cancel = true;
                    }
                }

                return cancel;
            });

    boolean cancelSlow(Entity entity, BlockPos pos);
}
