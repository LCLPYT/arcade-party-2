package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

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

    boolean onSpectate(ServerPlayerEntity spectator, Entity target);
}
