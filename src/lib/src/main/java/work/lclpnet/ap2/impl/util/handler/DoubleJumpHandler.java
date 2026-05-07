package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerToggleFlightCallback;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DoubleJumpHandler {

    private final Predicate<ServerPlayer> predicate;
    private final Hook<OnDoubleJump> hook;
    private final Set<UUID> enabled = new HashSet<>();

    public DoubleJumpHandler(Predicate<ServerPlayer> predicate) {
        this.predicate = predicate;

        hook = HookFactory.createArrayBacked(OnDoubleJump.class, hooks -> player -> {
            for (var hook : hooks) {
                hook.accept(player);
            }
        });
    }

    public void enable(Iterable<? extends ServerPlayer> players) {
        players.forEach(this::enable);
    }

    public void enable(ServerPlayer player) {
        enabled.add(player.getUUID());
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    public void disable(ServerPlayer player) {
        enabled.remove(player.getUUID());
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
    }

    public Action<OnDoubleJump> onDoubleJump() {
        return Action.create(hook);
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerToggleFlightCallback.HOOK, (player, fly) -> {
            if (!fly || player.isCreative()) {
                return false;
            }

            if (!predicate.test(player)) {
                return true;
            }

            hook.invoker().accept(player);

            VelocityModifier.setVelocity(player, player.getLookAngle().scale(1.3));

            ServerLevel serverWorld = player.level();

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            serverWorld.sendParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.1, 0.1, 0.1, 0.1);
            serverWorld.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1, 1);

            return true;
        });
    }

    public interface OnDoubleJump extends Consumer<ServerPlayer> {}
}
