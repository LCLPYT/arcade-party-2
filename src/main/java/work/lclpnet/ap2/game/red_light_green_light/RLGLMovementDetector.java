package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.ap2.impl.util.movement.MovementListener;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerInputCallback;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class RLGLMovementDetector {

    private static final double MIN_DISTANCE_SQ = 0.2 * 0.2;
    private final Hook<PlayerAction> hook = PlayerAction.createHook();
    private final Map<UUID, Vec3d> fixed = new HashMap<>();

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            onMove(player, to);
            return false;
        });

        hooks.registerHook(PlayerConnectionHooks.QUIT, player -> fixed.remove(player.getUuid()));
        hooks.registerHook(PlayerInputCallback.HOOK, this::onInput);
    }

    public void register(PlayerAction action) {
        hook.register(action);
    }

    public void fixPosition(ServerPlayerEntity player) {
        fixed.put(player.getUuid(), player.getPos());

        if (MovementListener.isMovementInput(player.getPlayerInput())) {
            hook.invoker().act(player);
        }
    }

    public void unfixPosition(ServerPlayerEntity player) {
        fixed.remove(player.getUuid());
    }

    public void unfixAll() {
        fixed.clear();
    }

    private void onMove(ServerPlayerEntity player, PositionRotation to) {
        Vec3d pos = fixed.getOrDefault(player.getUuid(), null);

        if (pos == null) return;

        double dx = pos.x - to.getX();
        double dy = pos.y - to.getY();
        double dz = pos.z - to.getZ();

        if (dx * dx + dy * dy + dz * dz >= MIN_DISTANCE_SQ) {
            hook.invoker().act(player);
        }
    }

    private void onInput(ServerPlayerEntity player, PlayerInput input) {
        if (fixed.containsKey(player.getUuid()) && MovementListener.isMovementInput(input)) {
            hook.invoker().act(player);
        }
    }
}
