package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Marker;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.core.type.ApMarkerEntity;
import work.lclpnet.kibu.access.misc.CustomNbt;

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

    public void spawn(Actor actor, @Nullable Marker marker) {
        if (marker != null) {
            actor.setPosition(marker.position());

            ((ApMarkerEntity) marker).ap2$setActor(actor);
        }

        if (!add(actor)) return;

        actor.onSpawn();

        ActorSpawnedCallback.HOOK.invoker().onSpawned(actor);
    }

    public void discard(Actor actor, @Nullable Marker marker) {
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

    public static Optional<ActorInfo> getActorNbt(Marker marker) {
        return CustomNbt.get(marker, ACTOR_INFO_CODEC);
    }

    public static void writeActorNbt(Marker marker, Actor actor) {
        CompoundTag actorCompound = new CompoundTag();
        CompoundTag actorNbt = actorCompound;

        ActorData<?> data = actor.createData();
        Identifier id = actor.getType().id();

        if (data != null) {
            actorNbt = data.encode(NbtOps.INSTANCE, actorCompound)
                    .map(elem -> (CompoundTag) elem)
                    .resultOrPartial(Util.prefix("Encoding actor data for %s: ".formatted(id), logger::error))
                    .orElse(actorCompound);
        }

        var info = new ActorInfo(id, actorNbt);

        CustomNbt.set(marker, ACTOR_INFO_CODEC, info);
    }

    public record ActorInfo(Identifier type, CompoundTag nbt) {

        public static final MapCodec<Identifier> TYPE_CODEC = Identifier.CODEC.fieldOf(ACTOR_TYPE_NBT_KEY);

        public static final Codec<ActorInfo> CODEC = CompoundTag.CODEC.flatXmap(
                nbt -> NbtOps.INSTANCE.getMap(nbt)
                        .flatMap(mapLike -> TYPE_CODEC.decode(NbtOps.INSTANCE, mapLike))
                        .map(id -> new ActorInfo(id, nbt)),
                actorInfo -> NbtOps.INSTANCE.getMap(actorInfo.nbt()).flatMap(_ -> TYPE_CODEC.encode(actorInfo.type(), NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                        .build(actorInfo.nbt())
                        .map(d -> (CompoundTag) d))
        );
    }
}
