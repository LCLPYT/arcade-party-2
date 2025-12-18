package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.kibu.hook.HookRegistrar;

public interface MovementBlocker {

    void init(HookRegistrar hooks);

    void enableMovement(ServerPlayer player);

    void disableMovement(ServerPlayer player, int durationTicks);

    boolean isMovementDisabled(ServerPlayer player);

    void setModifySpeedAttribute(boolean modifyAttributes);

    boolean shouldModifySpeedAttribute();
}
