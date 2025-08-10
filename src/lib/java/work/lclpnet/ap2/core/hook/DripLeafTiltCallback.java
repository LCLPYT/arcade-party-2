package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface DripLeafTiltCallback {

    Hook<DripLeafTiltCallback> HOOK = HookFactory.createArrayBacked(DripLeafTiltCallback.class, hooks -> (entity, pos) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onTilt(entity, pos)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onTilt(Entity entity, BlockPos pos);
}
