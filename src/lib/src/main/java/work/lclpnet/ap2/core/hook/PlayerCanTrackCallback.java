package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

    boolean canTrack(ServerPlayer player, Entity entity);
}
