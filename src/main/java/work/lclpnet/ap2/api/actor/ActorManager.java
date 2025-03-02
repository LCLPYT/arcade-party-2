package work.lclpnet.ap2.api.actor;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ActorManager implements Tickable {

    private final Set<Actor> actors = new LinkedHashSet<>();
    private final Set<Tickable> tickables = new LinkedHashSet<>();

    public synchronized boolean add(Actor actor) {
        Objects.requireNonNull(actor, "Actor is null");

        if (!actors.add(actor)) {
            return false;
        }

        if (actor instanceof Tickable tickable) {
            tickables.add(tickable);
        }

        return true;
    }

    public synchronized boolean remove(Actor actor) {
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
