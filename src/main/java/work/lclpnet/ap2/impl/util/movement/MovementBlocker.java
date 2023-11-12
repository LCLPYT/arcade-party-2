package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

public interface MovementBlocker {

    void init(HookRegistrar hooks);

    void enableMovement(ServerPlayerEntity player);

    void disableMovement(ServerPlayerEntity player, int durationTicks);

    boolean isMovementDisabled(ServerPlayerEntity player);

    void setUseStatusEffects(boolean useStatusEffects);

    boolean isUsingStatusEffects();
}
