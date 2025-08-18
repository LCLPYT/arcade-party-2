package work.lclpnet.ap2.impl.actor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.actor.ActorData;
import work.lclpnet.ap2.api.actor.ActorInit;
import work.lclpnet.ap2.api.actor.BaseActor;
import work.lclpnet.ap2.api.util.collision.MovementObserver;
import work.lclpnet.ap2.impl.util.CodecUtil;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShapes;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerJumpCallback;

import java.util.Set;
import java.util.WeakHashMap;

import static java.lang.Math.abs;
import static net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class GravityFieldActor extends BaseActor {

    private @Nullable MovementObserver observer = null;
    private @Nullable Manipulator manipulator = null;
    private @Nullable PlayerJumpCallback jumpCallback = null;
    private @Nullable HookRegistrar hooks = null;
    @Getter
    protected final BlockShape shape;
    @Getter @Setter
    private double strength;

    public GravityFieldActor(ActorInit init, Data data) {
        super(init);
        this.shape = data.shape();
        this.strength = data.strength();
    }

    public void enable(MovementObserver observer, Manipulator manipulator, HookRegistrar hooks) {
        this.observer = observer;
        this.manipulator = manipulator;
        this.hooks = hooks;

        observer.whenEntering(shape, this::onEnterField);
        observer.whenLeaving(shape, this::onLeaveField);

        jumpCallback = player -> {
            if (player.getWorld() == world && shape.contains(player.getX(), player.getY(), player.getZ())) {
                onPlayerJumped(player);
            }
            return false;
        };

        hooks.registerHook(PlayerJumpCallback.HOOK, jumpCallback);
    }

    @Override
    public void onRemove() {
        super.onRemove();

        if (observer != null) {
            observer.removeListeners(shape);
        }

        if (hooks != null && jumpCallback != null) {
            hooks.unregisterHook(PlayerJumpCallback.HOOK, jumpCallback);
        }
    }

    private void onPlayerJumped(ServerPlayerEntity player) {
        if (strength > GRAVITY.value().getDefaultValue()) return;

        double gravity = player.getAttributeBaseValue(GRAVITY);

        if (abs(gravity - strength) > 1e-5) return;

        player.getWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.25, player.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
    }

    private void onEnterField(ServerPlayerEntity player) {
        if (manipulator != null && player.getWorld() == world) {
            double change = manipulator.add(player, this);

            onGravityChanged(player, change);
        }
    }

    private void onLeaveField(ServerPlayerEntity player) {
        if (manipulator != null && player.getWorld() == world) {
            double change = manipulator.remove(player, this);

            onGravityChanged(player, change);
        }
    }

    public void onGravityChanged(ServerPlayerEntity player, double gravityDelta) {
        if (gravityDelta < 0) {
            player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_IDLE_GROUND, SoundCategory.PLAYERS, 0.55f, 1.5f);
            player.getWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        } else if (gravityDelta > 0) {
            player.playSoundToPlayer(SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.35f, 0.75f);
        }
    }

    @Override
    public @Nullable ActorData<?> createData() {
        return new ActorData<>(new Data(shape, strength), Data.CODEC);
    }

    public record Data(BlockShape shape, double strength) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockShapes.CODEC.fieldOf("shape").forGetter(Data::shape),
                CodecUtil.FINITE_DOUBLE.fieldOf("strength").forGetter(Data::strength)
        ).apply(instance, Data::new));
    }

    public static class Manipulator {
        private final WeakHashMap<ServerPlayerEntity, Entry> entries = new WeakHashMap<>();

        synchronized double add(ServerPlayerEntity player, GravityFieldActor field) {
            var entry = entries.computeIfAbsent(player, p
                    -> new Entry(new ObjectArraySet<>(1), p.getAttributeBaseValue(GRAVITY)));

            if (!entry.fields.add(field)) {
                return 0.0d;
            }

            return update(player, entry);
        }

        synchronized double remove(ServerPlayerEntity player, GravityFieldActor field) {
            var entry = entries.getOrDefault(player, null);

            if (entry == null || !entry.fields.remove(field)) {
                return 0.0d;
            }

            double change = update(player, entry);

            if (entry.fields.isEmpty()) {
                entries.remove(player);
            }

            return change;
        }

        double update(ServerPlayerEntity player, Entry entry) {
            var strength = entry.fields.stream()
                    .mapToDouble(GravityFieldActor::getStrength)
                    .max()
                    .orElse(entry.initialStrength);

            double prevTotalGravity = player.getAttributeValue(GRAVITY);

            setAttribute(player, GRAVITY, strength);

            return player.getAttributeValue(GRAVITY) - prevTotalGravity;
        }

        private record Entry(Set<GravityFieldActor> fields, double initialStrength) {}
    }
}
