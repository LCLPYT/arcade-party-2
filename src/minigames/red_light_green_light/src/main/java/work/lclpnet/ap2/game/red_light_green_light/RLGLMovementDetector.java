package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.impl.util.movement.MovementListener;
import work.lclpnet.gaco.collisions.util.PlayerAction;
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
    private final Map<UUID, Vec3> fixed = new HashMap<>();

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            onMove(player, to);
            return false;
        });

        hooks.registerHook(PlayerConnectionHooks.QUIT, player -> fixed.remove(player.getUUID()));
        hooks.registerHook(PlayerInputCallback.HOOK, this::onInput);
    }

    public void register(PlayerAction action) {
        hook.register(action);
    }

    public void fixPosition(ServerPlayer player) {
        fixed.put(player.getUUID(), player.position());

        if (MovementListener.isMovementInput(player.getLastClientInput())) {
            hook.invoker().act(player);
        }
    }

    public void unfixPosition(ServerPlayer player) {
        fixed.remove(player.getUUID());
    }

    public void unfixAll() {
        fixed.clear();
    }

    private void onMove(ServerPlayer player, PositionRotation to) {
        Vec3 pos = fixed.getOrDefault(player.getUUID(), null);

        if (pos == null) return;

        double dx = pos.x - to.x();
        double dy = pos.y - to.y();
        double dz = pos.z - to.z();

        if (dx * dx + dy * dy + dz * dz >= MIN_DISTANCE_SQ) {
            hook.invoker().act(player);
        }
    }

    private void onInput(ServerPlayer player, Input input) {
        if (fixed.containsKey(player.getUUID()) && MovementListener.isMovementInput(input)) {
            hook.invoker().act(player);
        }
    }
}
