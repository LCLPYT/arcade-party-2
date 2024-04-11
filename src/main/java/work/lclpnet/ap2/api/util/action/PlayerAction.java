package work.lclpnet.ap2.api.util.action;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerAction {

    void act(ServerPlayerEntity player);

    static Hook<PlayerAction> createHook() {
        return HookFactory.createArrayBacked(PlayerAction.class, callbacks -> player -> {
            for (PlayerAction callback : callbacks) {
                callback.act(player);
            }
        });
    }
}
