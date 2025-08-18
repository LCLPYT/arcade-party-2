package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerDeathMessageCallback {

    Hook<PlayerDeathMessageCallback> HOOK = HookFactory.createArrayBacked(PlayerDeathMessageCallback.class, hooks -> (player, source, currentMsg) -> {
        for (PlayerDeathMessageCallback hook : hooks) {
            currentMsg = hook.modifyDeathMessage(player, source, currentMsg);
        }

        return currentMsg;
    });

    Text modifyDeathMessage(ServerPlayerEntity player, DamageSource source, Text currentMsg);
}
