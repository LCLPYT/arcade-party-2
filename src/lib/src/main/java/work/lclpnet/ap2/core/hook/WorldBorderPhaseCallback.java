package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface WorldBorderPhaseCallback {

    Hook<WorldBorderPhaseCallback> HOOK = HookFactory.createArrayBacked(WorldBorderPhaseCallback.class, hooks -> (border, entity) -> {
        for (var hook : hooks) {
            if (hook.shouldPhase(border, entity)) {
                return true;
            }
        }

        return false;
    });

    /**
     * Whether the entity should pass through the world border as if it had no collision.
     * @param border The world border.
     * @param entity The entity that is about to collide with the border.
     * @return True, if the entity should phase through the border.
     */
    boolean shouldPhase(WorldBorder border, Entity entity);
}
