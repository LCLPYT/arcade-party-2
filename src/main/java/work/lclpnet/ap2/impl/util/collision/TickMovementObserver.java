package work.lclpnet.ap2.impl.util.collision;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.util.movement.TickMovementDetector;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.function.Predicate;

public class TickMovementObserver extends AbstractMovementObserver {

    public TickMovementObserver(CollisionDetector collisionDetector, Predicate<ServerPlayerEntity> predicate) {
        super(collisionDetector, predicate);
    }

    public void init(TaskScheduler scheduler, HookRegistrar hooks, MinecraftServer server) {
        TickMovementDetector detector = new TickMovementDetector(() -> PlayerLookup.all(server));
        detector.register(player -> onMove(player, player.getPos()));
        detector.init(scheduler, hooks);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            onMove(player, player.getPos());
        }
    }
}
