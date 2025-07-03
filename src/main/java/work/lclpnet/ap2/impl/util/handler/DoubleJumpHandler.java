package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

    private final Predicate<ServerPlayerEntity> predicate;
    private final Hook<OnDoubleJump> hook;
    private final Set<UUID> enabled = new HashSet<>();

    public DoubleJumpHandler(Predicate<ServerPlayerEntity> predicate) {
        this.predicate = predicate;

        hook = HookFactory.createArrayBacked(OnDoubleJump.class, hooks -> player -> {
            for (var hook : hooks) {
                hook.accept(player);
            }
        });
    }

    public void enable(Iterable<? extends ServerPlayerEntity> players) {
        players.forEach(this::enable);
    }

    public void enable(ServerPlayerEntity player) {
        enabled.add(player.getUuid());
        player.getAbilities().allowFlying = true;
        player.sendAbilitiesUpdate();
    }

    public void disable(ServerPlayerEntity player) {
        enabled.remove(player.getUuid());
        player.getAbilities().allowFlying = false;
        player.sendAbilitiesUpdate();
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

            VelocityModifier.setVelocity(player, player.getRotationVector().multiply(1.3));

            ServerWorld serverWorld = player.getWorld();

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            serverWorld.spawnParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.1, 0.1, 0.1, 0.1);
            serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1, 1);

            return true;
        });
    }

    public interface OnDoubleJump extends Consumer<ServerPlayerEntity> {}
}
