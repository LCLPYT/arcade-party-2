package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PlayerInput;
import work.lclpnet.kibu.hook.HookListenerModule;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.lobby.util.PlayerReset;

public class MovementListener implements HookListenerModule {

    private static final double TOL_SQ = 0.2 * 0.2;
    private final MovementBlocker blocker;
    private boolean registered = false;

    MovementListener(MovementBlocker blocker) {
        this.blocker = blocker;
    }

    static void modifyJumpAttribute(ServerPlayerEntity player) {
        PlayerReset.setAttribute(player, EntityAttributes.JUMP_STRENGTH, 0);
    }

    static void resetJumpAttributes(ServerPlayerEntity player) {
        PlayerReset.resetAttribute(player, EntityAttributes.JUMP_STRENGTH);
    }

    static void modifySpeedAttribute(ServerPlayerEntity player) {
        PlayerReset.setAttribute(player, EntityAttributes.MOVEMENT_SPEED, 0);
    }

    static void resetSpeedAttributes(ServerPlayerEntity player) {
        PlayerReset.resetAttribute(player, EntityAttributes.MOVEMENT_SPEED);
    }

    @Override
    public void registerListeners(HookRegistrar registrar) {
        if (registered) return;

        registered = true;

        registrar.registerHook(PlayerConnectionHooks.QUIT, blocker::enableMovement);
        registrar.registerHook(PlayerMoveCallback.HOOK, this::onPlayerMove);
    }

    private boolean onPlayerMove(ServerPlayerEntity player, PositionRotation from, PositionRotation to) {
        return blocker.isMovementDisabled(player) && (from.squaredDistanceTo(to) >= TOL_SQ || isMovementInput(player.getPlayerInput()));
    }

    public static boolean isMovementInput(PlayerInput input) {
        return input.jump() || input.forward() || input.backward() || input.left() || input.right();
    }
}
