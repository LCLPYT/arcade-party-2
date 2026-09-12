package work.lclpnet.ap2.core.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface CobwebEntityInsideCallback {

    Hook<CobwebEntityInsideCallback> HOOK = HookFactory.createArrayBacked(CobwebEntityInsideCallback.class, callbacks ->
            (entity, pos) -> {
                boolean cancel = false;

                for (var cb : callbacks) {
                    if (cb.onEntityInside(entity, pos)) {
                        cancel = true;
                    }
                }

                return cancel;
            });

    /**
     * Called every tick an entity is inside a cobweb block.
     *
     * @return true to cancel the cobweb effects for that entity
     */
    boolean onEntityInside(Entity entity, BlockPos pos);
}
