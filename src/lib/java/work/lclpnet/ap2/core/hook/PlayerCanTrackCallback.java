package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerCanTrackCallback {

    Hook<PlayerCanTrackCallback> HOOK = HookFactory.createArrayBacked(PlayerCanTrackCallback.class, hooks -> (player, entity) -> {
        for (var hook : hooks) {
            if (!hook.canTrack(player, entity)) {
                return false;
            }
        }

        return true;
    });

    boolean canTrack(ServerPlayerEntity player, Entity entity);
}
