package work.lclpnet.ap2.impl.util.collision;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlayerMovementObserver {

    private final CollisionDetector collisionDetector;
    private final WeakHashMap<ServerPlayerEntity, Collider> currentRegion = new WeakHashMap<>();
    private final Map<Collider, Consumer<ServerPlayerEntity>> regionEnter = new HashMap<>();
    private final Map<Collider, Consumer<ServerPlayerEntity>> regionLeave = new HashMap<>();
    private BiConsumer<ServerPlayerEntity, Collider> onEnter = null, onLeave = null;

    public PlayerMovementObserver(CollisionDetector collisionDetector) {
        this.collisionDetector = collisionDetector;
    }

    public void init(HookRegistrar registrar, MinecraftServer server) {
        registrar.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            onMove(player, to);
            return false;
        });

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            onMove(player, player.getPos());
        }
    }

    private void onMove(ServerPlayerEntity player, Position pos) {
        Collider region = collisionDetector.getCollision(pos);
        Collider lastRegion = currentRegion.get(player);

        if (lastRegion == region) return;

        currentRegion.put(player, region);

        if (lastRegion != null) {
            onLeave(player, lastRegion);
        }

        if (region != null) {
            onEnter(player, region);
        }
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

    public void setRegionEnterListener(BiConsumer<ServerPlayerEntity, Collider> onEnter) {
        this.onEnter = onEnter;
    }

    public void setRegionLeaveListener(BiConsumer<ServerPlayerEntity, Collider> onLeave) {
        this.onLeave = onLeave;
    }

    public void whenEntering(Collider region, Consumer<ServerPlayerEntity> action) {
        Objects.requireNonNull(region);
        Objects.requireNonNull(action);

        // make sure the collision detector knows about the region
        collisionDetector.add(region);

        regionEnter.put(region, action);
    }

    public void whenLeaving(Collider region, Consumer<ServerPlayerEntity> action) {
        Objects.requireNonNull(region);
        Objects.requireNonNull(action);

        // make sure the collision detector knows about the region
        collisionDetector.add(region);

        regionLeave.put(region, action);
    }
}
