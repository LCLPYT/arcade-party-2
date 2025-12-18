package work.lclpnet.ap2.core.hook;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerDisplayNameCallback {

    Hook<PlayerDisplayNameCallback> HOOK = HookFactory.createArrayBacked(PlayerDisplayNameCallback.class, hooks -> (player, name) -> {
        for (PlayerDisplayNameCallback hook : hooks) {
            name = hook.modifyDisplayName(player, name);
        }

        return name;
    });

    Component modifyDisplayName(Player player, Component name);
}
