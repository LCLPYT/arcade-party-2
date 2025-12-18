package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
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

    static void modifyJumpAttribute(ServerPlayer player) {
        PlayerReset.setAttribute(player, Attributes.JUMP_STRENGTH, 0);
    }

    static void resetJumpAttributes(ServerPlayer player) {
        PlayerReset.resetAttribute(player, Attributes.JUMP_STRENGTH);
    }

    static void modifySpeedAttribute(ServerPlayer player) {
        PlayerReset.setAttribute(player, Attributes.MOVEMENT_SPEED, 0);
    }

    static void resetSpeedAttributes(ServerPlayer player) {
        PlayerReset.resetAttribute(player, Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void registerListeners(HookRegistrar registrar) {
        if (registered) return;

        registered = true;

        registrar.registerHook(PlayerConnectionHooks.QUIT, blocker::enableMovement);
        registrar.registerHook(PlayerMoveCallback.HOOK, this::onPlayerMove);
    }

    private boolean onPlayerMove(ServerPlayer player, PositionRotation from, PositionRotation to) {
        return blocker.isMovementDisabled(player) && (from.squaredDistanceTo(to) >= TOL_SQ || isMovementInput(player.getLastClientInput()));
    }

    public static boolean isMovementInput(Input input) {
        return input.jump() || input.forward() || input.backward() || input.left() || input.right();
    }
}
