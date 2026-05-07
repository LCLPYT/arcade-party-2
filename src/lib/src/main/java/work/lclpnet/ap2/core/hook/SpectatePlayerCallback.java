package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface SpectatePlayerCallback {

    Hook<SpectatePlayerCallback> HOOK = HookFactory.createArrayBacked(SpectatePlayerCallback.class, callbacks -> (spectator, target) -> {
        boolean cancel = false;

        for (var cb : callbacks) {
            if (cb.onSpectate(spectator, target)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onSpectate(ServerPlayer spectator, Entity target);
}
