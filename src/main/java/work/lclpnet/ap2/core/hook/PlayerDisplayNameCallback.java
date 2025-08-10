package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface PlayerDisplayNameCallback {

    Hook<PlayerDisplayNameCallback> HOOK = HookFactory.createArrayBacked(PlayerDisplayNameCallback.class, hooks -> (player, name) -> {
        for (PlayerDisplayNameCallback hook : hooks) {
            name = hook.modifyDisplayName(player, name);
        }

        return name;
    });

    Text modifyDisplayName(PlayerEntity player, Text name);
}
