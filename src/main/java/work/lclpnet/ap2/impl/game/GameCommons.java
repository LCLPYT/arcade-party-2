package work.lclpnet.ap2.impl.game;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Objects;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    @Nullable
    private final GameMap map;

    public GameCommons(MiniGameHandle gameHandle, @Nullable GameMap map) {
        this.gameHandle = gameHandle;
        this.map = map;
    }

    public Action<PlayerAction> whenBelowCriticalHeight() {
        Objects.requireNonNull(map);

        Number minY = map.getProperty("critical-height");

        if (minY == null) return Action.noop();

        return whenBelowY(minY.doubleValue());
    }

    public Action<PlayerAction> whenBelowY(double minY) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        var hook = HookFactory.createArrayBacked(PlayerAction.class, callbacks -> player -> {
            for (PlayerAction callback : callbacks) {
                callback.onAction(player);
            }
        });

        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            if (!participants.isParticipating(player)) return false;

            if (!(to.getY() >= minY)) {
                hook.invoker().onAction(player);
            }

            return false;
        });

        return Action.create(hook);
    }
}
