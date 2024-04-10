package work.lclpnet.ap2.impl.util.collision;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.function.Predicate;

public class TickMovementObserver extends AbstractMovementObserver {

    public TickMovementObserver(CollisionDetector collisionDetector, Predicate<ServerPlayerEntity> predicate) {
        super(collisionDetector, predicate);
    }

    public void init(TaskScheduler task, MinecraftServer server) {
        task.interval(() -> tick(server), 1);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            onMove(player, player.getPos());
        }
    }

    private void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            onMove(player, player.getPos());
        }
    }
}
