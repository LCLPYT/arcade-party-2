package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.core.type.ApMarkerEntity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ActorManager implements Tickable {

    public static final String
            ACTOR_NBT_KEY = "gca:actor",
            ACTOR_TYPE_NBT_KEY = "type";

    public static final MapCodec<ActorInfo> ACTOR_INFO_CODEC = ActorInfo.CODEC.fieldOf(ACTOR_NBT_KEY);

    private static final Logger logger = LoggerFactory.getLogger(ActorManager.class);

    private final Set<Actor> actors = new LinkedHashSet<>();
    private final Set<Tickable> tickables = new LinkedHashSet<>();

    public void spawn(Actor actor, @Nullable MarkerEntity marker) {
        if (marker != null) {
            actor.setPosition(marker.getPos());

            ((ApMarkerEntity) marker).ap2$setActor(actor);
        }

        if (!add(actor)) return;

        actor.onSpawn();

        ActorSpawnedCallback.HOOK.invoker().onSpawned(actor);
    }

    public void discard(Actor actor, @Nullable MarkerEntity marker) {
        if (marker != null) {
            ((ApMarkerEntity) marker).ap2$setActor(null);
        }

        if (!remove(actor)) return;

        actor.onRemove();

        ActorRemovedCallback.HOOK.invoker().onRemoved(actor);
    }

    private synchronized boolean add(Actor actor) {
        Objects.requireNonNull(actor, "Actor is null");

        if (!actors.add(actor)) {
            return false;
        }

        if (actor instanceof Tickable tickable) {
            tickables.add(tickable);
        }

        return true;
    }

    private synchronized boolean remove(Actor actor) {
        if (!actors.remove(actor)) {
            return false;
        }

        if (actor instanceof Tickable tickable) {
            tickables.remove(tickable);
        }

        return true;
    }

    @Override
    public synchronized void tick() {
        for (Tickable tickable : tickables) {
            tickable.tick();
        }
    }

    public static Optional<ActorInfo> getActorNbt(MarkerEntity marker) {
        NbtComponent customData = marker.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);

        return customData.get(ACTOR_INFO_CODEC).result();
    }

    public static void writeActorNbt(MarkerEntity marker, Actor actor) {
        NbtCompound actorCompound = new NbtCompound();
        NbtCompound actorNbt = actorCompound;

        ActorData<?> data = actor.createData();
        Identifier id = actor.getType().id();

        if (data != null) {
            actorNbt = data.encode(NbtOps.INSTANCE, actorCompound)
                    .map(elem -> (NbtCompound) elem)
                    .resultOrPartial(Util.addPrefix("Encoding actor data for %s: ".formatted(id), logger::error))
                    .orElse(actorCompound);
        }

        var info = new ActorInfo(id, actorNbt);

        NbtComponent customData = marker.get(DataComponentTypes.CUSTOM_DATA);

        if (customData == null) return;

        customData.with(NbtOps.INSTANCE, ACTOR_INFO_CODEC, info)
                .ifSuccess(component -> marker.setComponent(DataComponentTypes.CUSTOM_DATA, component));
    }

    public record ActorInfo(Identifier type, NbtCompound nbt) {

        public static final MapCodec<Identifier> TYPE_CODEC = Identifier.CODEC.fieldOf(ACTOR_TYPE_NBT_KEY);

        public static final Codec<ActorInfo> CODEC = NbtCompound.CODEC.flatXmap(
                nbt -> NbtOps.INSTANCE.getMap(nbt)
                        .flatMap(mapLike -> TYPE_CODEC.decode(NbtOps.INSTANCE, mapLike))
                        .map(id -> new ActorInfo(id, nbt)),
                actorInfo -> NbtOps.INSTANCE.getMap(actorInfo.nbt()).flatMap(mapLike -> TYPE_CODEC.encode(actorInfo.type(), NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                        .build(actorInfo.nbt())
                        .map(d -> (NbtCompound) d))
        );
    }
}
