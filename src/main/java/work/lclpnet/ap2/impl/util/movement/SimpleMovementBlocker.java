package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleMovementBlocker implements MovementBlocker {

    private final TaskScheduler scheduler;
    private final MovementListener movement = new MovementListener(this);
    private final Map<UUID, TaskHandle> blocked = new HashMap<>();
    private boolean useStatusEffects = true;

    public SimpleMovementBlocker(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void init(HookRegistrar hooks) {
        movement.registerListeners(hooks);
    }

    public void disableMovement(ServerPlayerEntity player) {
        if (useStatusEffects) {
            MovementListener.addStatusEffects(player, -1);
        }

        TaskHandle prev = blocked.put(player.getUuid(), null);

        if (prev != null) prev.cancel();
    }

    @Override
    public void disableMovement(ServerPlayerEntity player, int durationTicks) {
        if (durationTicks <= 0) {
            disableMovement(player);
            return;
        }

        if (useStatusEffects) {
            MovementListener.addStatusEffects(player, durationTicks);
        }

        TaskHandle task = scheduler.timeout(() -> enableMovement(player), durationTicks);
        TaskHandle prev = blocked.put(player.getUuid(), task);

        if (prev != null) prev.cancel();
    }

    @Override
    public void enableMovement(ServerPlayerEntity player) {
        MovementListener.removeStatusEffects(player);

        TaskHandle task = blocked.remove(player.getUuid());

        if (task != null) task.cancel();
    }

    @Override
    public boolean isMovementDisabled(ServerPlayerEntity player) {
        return blocked.containsKey(player.getUuid());
    }

    @Override
    public void setUseStatusEffects(boolean useStatusEffects) {
        this.useStatusEffects = useStatusEffects;
    }

    @Override
    public boolean isUsingStatusEffects() {
        return useStatusEffects;
    }
}
