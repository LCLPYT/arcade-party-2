package work.lclpnet.ap2.impl.actor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.actor.ActorData;
import work.lclpnet.ap2.api.actor.ActorInit;
import work.lclpnet.ap2.api.actor.BaseActor;
import work.lclpnet.ap2.api.util.collision.MovementObserver;
import work.lclpnet.ap2.impl.util.CodecUtil;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.ap2.impl.util.world.stage.BlockShapes;

import java.util.Set;
import java.util.WeakHashMap;

import static net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class GravityFieldActor extends BaseActor {

    private @Nullable MovementObserver observer = null;
    private @Nullable Manipulator manipulator = null;
    @Getter
    protected final BlockShape shape;
    @Getter @Setter
    private double strength;

    public GravityFieldActor(ActorInit init, Data data) {
        super(init);
        this.shape = data.shape();
        this.strength = data.strength();
    }

    public void enable(MovementObserver observer, Manipulator manipulator) {
        this.observer = observer;
        this.manipulator = manipulator;

        observer.whenEntering(shape, this::onEnterField);
        observer.whenLeaving(shape, this::onLeaveField);
    }

    @Override
    public void onRemove() {
        super.onRemove();

        if (observer != null) {
            observer.removeListeners(shape);
        }
    }

    private void onEnterField(ServerPlayerEntity player) {
        if (manipulator != null && player.getServerWorld() == world) {
            manipulator.add(player, this);
        }
    }

    private void onLeaveField(ServerPlayerEntity player) {
        if (manipulator != null && player.getServerWorld() == world) {
            manipulator.remove(player, this);
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

        synchronized void add(ServerPlayerEntity player, GravityFieldActor field) {
            var entry = entries.computeIfAbsent(player, p
                    -> new Entry(new ObjectArraySet<>(1), p.getAttributeBaseValue(GRAVITY)));

            if (!entry.fields.add(field)) return;

            update(player, entry);
        }

        synchronized void remove(ServerPlayerEntity player, GravityFieldActor field) {
            var entry = entries.getOrDefault(player, null);

            if (entry == null || !entry.fields.remove(field)) return;

            update(player, entry);

            if (entry.fields.isEmpty()) {
                entries.remove(player);
            }
        }

        void update(ServerPlayerEntity player, Entry entry) {
            var strength = entry.fields.stream()
                    .mapToDouble(GravityFieldActor::getStrength)
                    .max()
                    .orElse(entry.initialStrength);

            setAttribute(player, GRAVITY, strength);
        }

        private record Entry(Set<GravityFieldActor> fields, double initialStrength) {}
    }
}
