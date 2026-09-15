package work.lclpnet.ap2.core.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

/**
 * Invoked when an entity triggers an unpowered trip wire.
 */
public interface TripWireTriggerCallback {

    Hook<TripWireTriggerCallback> HOOK = HookFactory.createArrayBacked(TripWireTriggerCallback.class, hooks -> (level, pos, entity) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onTrigger(level, pos, entity)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onTrigger(Level level, BlockPos pos, Entity entity);
}
