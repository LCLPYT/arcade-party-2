package work.lclpnet.ap2.impl.util.collision;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;

import java.util.function.Predicate;

public class PlayerMovementObserver extends AbstractMovementObserver {

    public PlayerMovementObserver(CollisionDetector collisionDetector, Predicate<ServerPlayerEntity> predicate) {
        super(collisionDetector, predicate);
    }

    public void init(HookRegistrar registrar, MinecraftServer server) {
        registrar.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            onMove(player, to);
            return false;
        });

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            onMove(player, player.getPos());
        }
    }
}
