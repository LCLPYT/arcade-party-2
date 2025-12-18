package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface EntitySpawnCallback {

    Hook<EntitySpawnCallback> HOOK = HookFactory.createArrayBacked(EntitySpawnCallback.class, hooks -> (entity, world) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onSpawn(entity, world)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onSpawn(Entity entity, ServerLevel world);
}
