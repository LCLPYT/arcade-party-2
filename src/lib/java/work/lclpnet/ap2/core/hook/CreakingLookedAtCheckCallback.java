package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.monster.creaking.Creaking;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.util.PendingResult;

public interface CreakingLookedAtCheckCallback {

    Hook<CreakingLookedAtCheckCallback> HOOK = HookFactory.createArrayBacked(CreakingLookedAtCheckCallback.class, hooks -> creaking -> {
        for (var hook : hooks) {
            var res = hook.isBeingLookedAt(creaking);

            if (res.isPass()) continue;

            return res;
        }

        return PendingResult.pass();
    });

    PendingResult<Boolean> isBeingLookedAt(Creaking creaking);
}
