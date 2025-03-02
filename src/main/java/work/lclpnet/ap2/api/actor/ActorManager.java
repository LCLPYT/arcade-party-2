package work.lclpnet.ap2.api.actor;

import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.core.type.ApMarkerEntity;
import work.lclpnet.kibu.access.entity.MarkerEntityAccess;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ActorManager implements Tickable {

    public static final String
            ACTOR_NBT_KEY = "gca:actor",
            ACTOR_TYPE_NBT_KEY = "type";

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

    public static @Nullable NbtCompound getActorNbt(MarkerEntity marker) {
        NbtCompound data = MarkerEntityAccess.getData(marker);

        if (!data.contains(ACTOR_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }

        return data.getCompound(ACTOR_NBT_KEY);
    }

    public static void writeActorNbt(MarkerEntity marker, Actor actor) {
        NbtCompound actorCompound = new NbtCompound();
        NbtElement actorNbt = actorCompound;

        String typeStr = actor.getType().id().toString();
        actorCompound.putString(ACTOR_TYPE_NBT_KEY, typeStr);

        ActorData<?> actorData = actor.createData();

        if (actorData != null) {
            actorNbt = actorData.encode(NbtOps.INSTANCE, actorCompound)
                    .resultOrPartial(Util.addPrefix("Encoding actor data for %s: ".formatted(typeStr), logger::error))
                    .orElse(actorCompound);
        }

        NbtCompound markerNbt = MarkerEntityAccess.getData(marker);
        markerNbt.put(ACTOR_NBT_KEY, actorNbt);
    }
}
