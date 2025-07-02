package work.lclpnet.ap2.impl.game.kit;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import lombok.Getter;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KitManager implements KitReadView {

    @Getter
    private final List<Kit> kits;
    private final BiMap<String, Kit> kitById;
    private final Map<UUID, Kit> playerKits = new HashMap<>();

    public KitManager(List<Kit> kits) {
        if (kits.isEmpty()) {
            throw new IllegalArgumentException("At least one kit is required");
        }

        var kitById = HashBiMap.<String, Kit>create(kits.size());

        for (Kit kit : kits) {
            if (kitById.containsKey(kit.id())) {
                throw new IllegalArgumentException("Kit with id '%s' already exists.".formatted(kit.id()));
            }

            kitById.put(kit.id(), kit);
        }

        this.kitById = ImmutableBiMap.copyOf(kitById);
        this.kits = List.copyOf(kits);
    }

    public void init() {
        for (Kit kit : kits) {
            kit.init();
        }
    }

    public void setupPlayerKits(Iterable<? extends ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            changeKit(player, defaultKit());
        }
    }

    public Kit defaultKit() {
        return kits.getFirst();
    }

    @Override
    public synchronized @NotNull Kit getKit(ServerPlayerEntity player) {
        return playerKits.getOrDefault(player.getUuid(), defaultKit());
    }

    @Override
    public synchronized boolean hasKitEquipped(ServerPlayerEntity player, Kit kit) {
        return getKit(player).equals(kit);
    }

    public synchronized void changeKit(ServerPlayerEntity player, Kit kit) {
        validateKit(kit);

        getKit(player).unequip(player);

        playerKits.put(player.getUuid(), kit);

        kit.equip(player);
    }

    private void validateKit(Kit kit) {
        if (!kitById.containsValue(kit)) {
            throw new IllegalArgumentException("Invalid kit");
        }
    }
}
