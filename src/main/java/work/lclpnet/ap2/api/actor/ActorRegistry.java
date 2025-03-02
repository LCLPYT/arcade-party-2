package work.lclpnet.ap2.api.actor;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ActorRegistry {

    private final Map<Identifier, ActorFactory<?>> types = new HashMap<>();

    public <T extends Actor> void register(Identifier id, ActorFactory<T> type) {
        Objects.requireNonNull(id, "Actor id is null");
        Objects.requireNonNull(type, "Actor type is null");

        types.put(id, type);
    }

    public Optional<ActorFactory<?>> get(Identifier id) {
        return Optional.ofNullable(types.getOrDefault(id, null));
    }
}
