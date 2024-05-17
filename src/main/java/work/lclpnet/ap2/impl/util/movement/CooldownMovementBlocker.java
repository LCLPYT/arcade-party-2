package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.util.handler.Cooldown;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class CooldownMovementBlocker implements MovementBlocker {

    private final Cooldown cooldown;
    private final MovementListener movement = new MovementListener(this);
    private boolean modifyAttributes = true;

    public CooldownMovementBlocker(TaskScheduler scheduler) {
        this.cooldown = new Cooldown(scheduler);
        this.cooldown.setOnCooldownOver(this::onCooldownOver);
    }

    @Override
    public void init(HookRegistrar hooks) {
        movement.registerListeners(hooks);
    }

    @Override
    public void enableMovement(ServerPlayerEntity player) {
        cooldown.resetCooldown(player);
    }

    @Override
    public void disableMovement(ServerPlayerEntity player, int durationTicks) {
        if (durationTicks <= 0) return;

        if (modifyAttributes) {
            MovementListener.modifyAttributes(player);
        }

        cooldown.setCooldown(player, durationTicks);
    }

    @Override
    public boolean isMovementDisabled(ServerPlayerEntity player) {
        return cooldown.isOnCooldown(player);
    }

    @Override
    public void setModifyAttributes(boolean modifyAttributes) {
        this.modifyAttributes = modifyAttributes;
    }

    @Override
    public boolean isUsingStatusEffects() {
        return modifyAttributes;
    }

    private void onCooldownOver(ServerPlayerEntity player) {
        if (modifyAttributes) {
            MovementListener.resetAttributes(player);
        }
    }
}
