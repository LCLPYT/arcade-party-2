package work.lclpnet.ap2.core.hook;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerEliminatedCallback {

    Hook<PlayerEliminatedCallback> HOOK = HookFactory.createArrayBacked(PlayerEliminatedCallback.class, hooks -> player -> {
        for (var hook : hooks) {
            hook.onEliminated(player);
        }
    });

    void onEliminated(ServerPlayerEntity player);
}
