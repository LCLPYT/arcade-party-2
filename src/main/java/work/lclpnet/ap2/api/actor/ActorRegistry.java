package work.lclpnet.ap2.api.actor;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ActorRegistry {

    private final Map<Identifier, ActorType<?>> types = new HashMap<>();

    public void register(ActorType<?> type) {
        Objects.requireNonNull(type, "Actor type is null");

        types.put(type.id(), type);
    }

    public Optional<ActorType<?>> getType(Identifier id) {
        return Optional.ofNullable(types.getOrDefault(id, null));
    }
}
