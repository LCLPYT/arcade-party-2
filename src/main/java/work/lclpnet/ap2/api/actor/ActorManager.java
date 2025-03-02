package work.lclpnet.ap2.api.actor;

import net.minecraft.entity.MarkerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.type.ApMarkerEntity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ActorManager implements Tickable {

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
}
