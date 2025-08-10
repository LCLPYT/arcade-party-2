package work.lclpnet.ap2.core.hook;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.Collection;

/**
 * Invoked when a player joins the server.
 * Can be used to modify the players which should be visible by the newly joined player.
 * E.g. players can be excluded (vanished) and the newly joined player won't be able to physically see them in the world, as well as in the player list.
 */
public interface PlayerListEntriesOnJoinCallback {

    Hook<PlayerListEntriesOnJoinCallback> HOOK = HookFactory.createArrayBacked(PlayerListEntriesOnJoinCallback.class, hooks -> (players) -> {
        for (var hook : hooks) {
            players = hook.shouldBeSent(players);
        }

        return players;
    });

    Collection<ServerPlayerEntity> shouldBeSent(Collection<ServerPlayerEntity> players);
}
