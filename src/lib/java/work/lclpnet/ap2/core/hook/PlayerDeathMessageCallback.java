package work.lclpnet.ap2.core.hook;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerDeathMessageCallback {

    Hook<PlayerDeathMessageCallback> HOOK = HookFactory.createArrayBacked(PlayerDeathMessageCallback.class, hooks -> (player, source, currentMsg) -> {
        for (PlayerDeathMessageCallback hook : hooks) {
            currentMsg = hook.modifyDeathMessage(player, source, currentMsg);
        }

        return currentMsg;
    });

    Component modifyDeathMessage(ServerPlayer player, DamageSource source, Component currentMsg);
}
