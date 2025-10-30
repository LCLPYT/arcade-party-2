package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.passive.CopperGolemEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface CopperGolemTurnIntoStatueCallback {

    Hook<CopperGolemTurnIntoStatueCallback> HOOK = HookFactory.createArrayBacked(CopperGolemTurnIntoStatueCallback.class, hooks -> (copperGolem) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onTurnIntoStatue(copperGolem)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onTurnIntoStatue(CopperGolemEntity copperGolem);
}
