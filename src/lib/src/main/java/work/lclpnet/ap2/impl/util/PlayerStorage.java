package work.lclpnet.ap2.impl.util;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlayerStorage<T> {

    private final Map<UUID, T> storage = new HashMap<>();
    private final Function<ServerPlayer, T> factory;

    public PlayerStorage(Function<ServerPlayer, T> factory, Map<ServerPlayer, T> initial) {
        this.factory = factory;

        if (initial != null) {
            for (var entry : initial.entrySet()) {
                storage.put(entry.getKey().getUUID(), entry.getValue());
            }
        }
    }

    public T get(ServerPlayer player) {
        return get(player, factory);
    }

    public T get(ServerPlayer player, Supplier<T> supplier) {
        return get(player, _ -> supplier.get());
    }

    public T get(ServerPlayer player, Function<ServerPlayer, T> factory) {
        return storage.computeIfAbsent(player.getUUID(), _ -> factory.apply(player));
    }

    public Optional<T> optional(ServerPlayer player) {
        return Optional.ofNullable(storage.get(player.getUUID()));
    }

    public static <T> PlayerStorage<T> create(Function<ServerPlayer, T> factory) {
        return new PlayerStorage<>(factory, null);
    }

    public static <T> PlayerStorage<T> create(Supplier<T> supplier) {
        return new PlayerStorage<>(_ -> supplier.get(), null);
    }

    public static <T> PlayerStorage<T> ofFixed(Map<ServerPlayer, T> values) {
        return new PlayerStorage<>(_ -> {
            throw new UnsupportedOperationException("Default factory is undefined");
        }, values);
    }
}
