package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.impl.util.handler.VisualCooldown;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class CooldownMovementBlocker implements MovementBlocker {

    private final VisualCooldown cooldown;
    private final MovementListener movement = new MovementListener(this);
    private boolean modifySpeedAttribute = true;

    public CooldownMovementBlocker(TaskScheduler scheduler) {
        this.cooldown = new VisualCooldown(scheduler);
        this.cooldown.setOnCooldownOver(this::resetAttributes);
    }

    @Override
    public void init(HookRegistrar hooks) {
        movement.registerListeners(hooks);
    }

    @Override
    public void enableMovement(ServerPlayer player) {
        cooldown.resetCooldown(player);
    }

    @Override
    public void disableMovement(ServerPlayer player, int durationTicks) {
        if (durationTicks <= 0) return;

        applyAttributes(player);

        cooldown.setCooldown(player, durationTicks);
    }

    @Override
    public boolean isMovementDisabled(ServerPlayer player) {
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

    private void applyAttributes(ServerPlayer player) {
        if (modifySpeedAttribute) {
            MovementListener.modifySpeedAttribute(player);
        }

        MovementListener.modifyJumpAttribute(player);
    }

    private void resetAttributes(ServerPlayer player) {
        if (modifySpeedAttribute) {
            MovementListener.resetSpeedAttributes(player);
        }

        MovementListener.resetJumpAttributes(player);
    }
}
