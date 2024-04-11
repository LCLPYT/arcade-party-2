package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.api.util.collision.MovementObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AbstractMovementObserver implements MovementObserver {

    private final CollisionDetector collisionDetector;
    private final Predicate<ServerPlayerEntity> predicate;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<Collider, Consumer<ServerPlayerEntity>> regionEnter = new HashMap<>();
    private final Map<Collider, Consumer<ServerPlayerEntity>> regionLeave = new HashMap<>();
    private BiConsumer<ServerPlayerEntity, Collider> onEnter = null, onLeave = null;

    public AbstractMovementObserver(CollisionDetector collisionDetector, Predicate<ServerPlayerEntity> predicate) {
        this.collisionDetector = collisionDetector;
        this.predicate = predicate;
    }

    @Override
    public void setRegionEnterListener(BiConsumer<ServerPlayerEntity, Collider> onEnter) {
        this.onEnter = onEnter;
    }

    @Override
    public void setRegionLeaveListener(BiConsumer<ServerPlayerEntity, Collider> onLeave) {
        this.onLeave = onLeave;
    }

    @Override
    public void whenEntering(Collider region, Consumer<ServerPlayerEntity> action) {
        Objects.requireNonNull(region);
        Objects.requireNonNull(action);

        // make sure the collision detector knows about the region
        collisionDetector.add(region);

        regionEnter.put(region, action);
    }

    @Override
    public void whenLeaving(Collider region, Consumer<ServerPlayerEntity> action) {
        Objects.requireNonNull(region);
        Objects.requireNonNull(action);

        // make sure the collision detector knows about the region
        collisionDetector.add(region);

        regionLeave.put(region, action);
    }

    protected void onMove(ServerPlayerEntity player, Position pos) {
        if (!predicate.test(player)) return;

        Entry entry = entries.computeIfAbsent(player.getUuid(), uuid -> new Entry());

        collisionDetector.updateCollisions(pos, entry.current);

        if (entry.last.equals(entry.current)) return;

        for (var left : entry.last.diff(entry.current)) {
            onLeave(player, left);
        }

        for (var entered : entry.current.diff(entry.last)) {
            onEnter(player, entered);
        }

        entry.last.set(entry.current);
    }

    private void onEnter(ServerPlayerEntity player, @NotNull Collider region) {
        if (onEnter != null) {
            onEnter.accept(player, region);
        }

        var action = regionEnter.get(region);

        if (action != null) {
            action.accept(player);
        }
    }

    private void onLeave(ServerPlayerEntity player, @NotNull Collider region) {
        if (onLeave != null) {
            onLeave.accept(player, region);
        }

        var action = regionLeave.get(region);

        if (action != null) {
            action.accept(player);
        }
    }

    private static class Entry {
        final CollisionInfo current = new CollisionInfo(1), last = new CollisionInfo(1);
    }
}
