package work.lclpnet.ap2.impl.util.movement;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.plugin.hook.HookListenerModule;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

class MovementListener implements HookListenerModule {

    private final MovementBlocker blocker;
    private boolean registered = false;

    MovementListener(MovementBlocker blocker) {
        this.blocker = blocker;
    }

    static void addStatusEffects(ServerPlayerEntity player, int duration) {
        var slowness = new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 255, false, false, true);
        var noJump = new StatusEffectInstance(StatusEffects.JUMP_BOOST, duration, 200, false, false, true);

        player.addStatusEffect(slowness);
        player.addStatusEffect(noJump);
    }

    static void removeStatusEffects(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
    }

    @Override
    public void registerListeners(HookRegistrar registrar) {
        if (registered) return;

        registered = true;

        registrar.registerHook(PlayerConnectionHooks.QUIT, blocker::enableMovement);
        registrar.registerHook(PlayerMoveCallback.HOOK, this::onPlayerMove);
    }

    private boolean onPlayerMove(ServerPlayerEntity player, PositionRotation from, PositionRotation to) {
        return blocker.isMovementDisabled(player) && from.isDifferentPosition(to) && from.squaredDistanceTo(to) <= 1;
    }
}
