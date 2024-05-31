package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.util.handler.Cooldown;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class CooldownMovementBlocker implements MovementBlocker {

    private final Cooldown cooldown;
    private final MovementListener movement = new MovementListener(this);
    private boolean modifySpeedAttribute = true;

    public CooldownMovementBlocker(TaskScheduler scheduler) {
        this.cooldown = new Cooldown(scheduler);
        this.cooldown.setOnCooldownOver(this::resetAttributes);
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

        applyAttributes(player);

        cooldown.setCooldown(player, durationTicks);
    }

    @Override
    public boolean isMovementDisabled(ServerPlayerEntity player) {
        return cooldown.isOnCooldown(player);
    }

    @Override
    public void setModifySpeedAttribute(boolean modifySpeedAttribute) {
        this.modifySpeedAttribute = modifySpeedAttribute;
    }

    @Override
    public boolean shouldModifySpeedAttribute() {
        return modifySpeedAttribute;
    }

    private void applyAttributes(ServerPlayerEntity player) {
        if (modifySpeedAttribute) {
            MovementListener.modifySpeedAttribute(player);
        }

        MovementListener.modifyJumpAttribute(player);
    }

    private void resetAttributes(ServerPlayerEntity player) {
        if (modifySpeedAttribute) {
            MovementListener.resetSpeedAttributes(player);
        }

        MovementListener.resetJumpAttributes(player);
    }
}
