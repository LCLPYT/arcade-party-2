package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class TickMovementDetector implements Runnable {

    private static final double MIN_DISTANCE_SQ = 1.0E-6;
    private final Hook<PlayerAction> hook = PlayerAction.createHook();
    private final Supplier<Iterable<? extends ServerPlayerEntity>> players;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private TaskHandle task = null;

    public TickMovementDetector(Supplier<Iterable<? extends ServerPlayerEntity>> players) {
        this.players = players;
    }

    public void register(PlayerAction action) {
        hook.register(action);
    }

    public void init(TaskScheduler scheduler, HookRegistrar hooks) {
        init(scheduler, hooks, 1);
    }

    public void init(TaskScheduler scheduler, HookRegistrar hooks, int tickRate) {
        tickRate = Math.max(1, tickRate);

        boolean wasInitialized = false;

        synchronized (this) {
            if (task != null) {
                task.cancel();
                wasInitialized = true;
            }

            task = scheduler.interval(this, tickRate);
        }

        if (!wasInitialized) {
            hooks.registerHook(PlayerConnectionHooks.QUIT, player -> entries.remove(player.getUuid()));
        }
    }

    @NotNull
    private Entry getEntry(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), uuid -> new Entry());
    }

    @Override
    public void run() {
        for (ServerPlayerEntity player : players.get()) {
            Entry entry = getEntry(player);

            boolean moved = entry.update(player.getX(), player.getY(), player.getZ());

            if (moved) {
                hook.invoker().act(player);
            }
        }
    }

    private static class Entry {
        private boolean init = true;
        private double lastX, lastY, lastZ;

        public boolean update(double x, double y, double z) {
            if (init) {
                init = false;
                lastX = x;
                lastY = y;
                lastZ = z;
                return false;
            }

            double dx = lastX - x;
            double dy = lastY - y;
            double dz = lastZ - z;

            if (dx * dx + dy * dy + dz * dz < MIN_DISTANCE_SQ) {
                return false;
            }

            lastX = x;
            lastY = y;
            lastZ = z;

            return true;
        }
    }
}
