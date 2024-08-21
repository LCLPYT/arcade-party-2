package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.HookListenerModule;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.lobby.util.PlayerReset;

class MovementListener implements HookListenerModule {

    private final MovementBlocker blocker;
    private boolean registered = false;

    MovementListener(MovementBlocker blocker) {
        this.blocker = blocker;
    }

    static void modifyJumpAttribute(ServerPlayerEntity player) {
        PlayerReset.setAttribute(player, EntityAttributes.GENERIC_JUMP_STRENGTH, 0);
    }

    static void resetJumpAttributes(ServerPlayerEntity player) {
        PlayerReset.resetAttribute(player, EntityAttributes.GENERIC_JUMP_STRENGTH);
    }

    static void modifySpeedAttribute(ServerPlayerEntity player) {
        PlayerReset.setAttribute(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0);
    }

    static void resetSpeedAttributes(ServerPlayerEntity player) {
        PlayerReset.resetAttribute(player, EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }

    @Override
    public void registerListeners(HookRegistrar registrar) {
        if (registered) return;

        registered = true;

        registrar.registerHook(PlayerConnectionHooks.QUIT, blocker::enableMovement);
        registrar.registerHook(PlayerMoveCallback.HOOK, this::onPlayerMove);
    }

    private boolean onPlayerMove(ServerPlayerEntity player, PositionRotation from, PositionRotation to) {
        return blocker.isMovementDisabled(player) && from.squaredDistanceTo(to) > 1e-3 && from.squaredDistanceTo(to) <= 1;
    }
}
