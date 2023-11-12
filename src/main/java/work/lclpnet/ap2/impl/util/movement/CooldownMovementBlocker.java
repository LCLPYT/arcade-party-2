package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.util.Cooldown;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class CooldownMovementBlocker implements MovementBlocker {

    private final Cooldown cooldown;
    private final MovementListener movement = new MovementListener(this);
    private boolean useStatusEffects = true;

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

        if (useStatusEffects) {
            MovementListener.addStatusEffects(player, durationTicks);
        }

        cooldown.setCooldown(player, durationTicks);
    }

    @Override
    public boolean isMovementDisabled(ServerPlayerEntity player) {
        return cooldown.isOnCooldown(player);
    }

    @Override
    public void setUseStatusEffects(boolean useStatusEffects) {
        this.useStatusEffects = useStatusEffects;
    }

    @Override
    public boolean isUsingStatusEffects() {
        return useStatusEffects;
    }

    private void onCooldownOver(ServerPlayerEntity player) {
        if (useStatusEffects) {
            MovementListener.removeStatusEffects(player);
        }
    }
}
