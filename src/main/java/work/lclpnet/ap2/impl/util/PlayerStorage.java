package work.lclpnet.ap2.impl.util;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlayerStorage<T> {

    private final Map<UUID, T> storage = new HashMap<>();
    private final Function<ServerPlayerEntity, T> factory;

    public PlayerStorage(Function<ServerPlayerEntity, T> factory, Map<ServerPlayerEntity, T> initial) {
        this.factory = factory;

        if (initial != null) {
            for (var entry : initial.entrySet()) {
                storage.put(entry.getKey().getUuid(), entry.getValue());
            }
        }
    }

    public T get(ServerPlayerEntity player) {
        return get(player, factory);
    }

    public T get(ServerPlayerEntity player, Supplier<T> supplier) {
        return get(player, p -> supplier.get());
    }

    public T get(ServerPlayerEntity player, Function<ServerPlayerEntity, T> factory) {
        return storage.computeIfAbsent(player.getUuid(), u -> factory.apply(player));
    }

    public Optional<T> optional(ServerPlayerEntity player) {
        return Optional.ofNullable(storage.get(player.getUuid()));
    }

    public static <T> PlayerStorage<T> create(Function<ServerPlayerEntity, T> factory) {
        return new PlayerStorage<>(factory, null);
    }

    public static <T> PlayerStorage<T> create(Supplier<T> supplier) {
        return new PlayerStorage<>(team -> supplier.get(), null);
    }

    public static <T> PlayerStorage<T> ofFixed(Map<ServerPlayerEntity, T> values) {
        return new PlayerStorage<>(team -> {
            throw new UnsupportedOperationException("Default factory is undefined");
        }, values);
    }
}
