package work.lclpnet.ap2.impl.util.world.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback;

import java.util.*;

/**
 * Manages {@link DynamicEntity} tracking for online players, so that they are only visible when nearby, just like real entities.
 * When tracking a {@link DynamicEntity}, each player can possibly see a different entity.
 * The actual "real" entity that is shown to a player in range depends on the {@link DynamicEntity} implementation.
 * @implNote This class doesn't do anything until {@link #init(TaskScheduler, HookRegistrar)} is called.
 */
public class DynamicEntityManager {

    private final Map<DynamicEntity, Tracker> entities = new HashMap<>();
    private final ServerWorld world;
    private final int serverViewDistance;

    public DynamicEntityManager(ServerWorld world) {
        this.world = world;

        MinecraftServer server = world.getServer();

        if (server instanceof MinecraftDedicatedServer dedicatedServer) {
            serverViewDistance = dedicatedServer.getProperties().viewDistance;
        } else {
            serverViewDistance = 10;
        }
    }

    /**
     * Initializes entity tracking.
     * @param scheduler The scheduler
     * @param hooks The hook registrar
     */
    public void init(TaskScheduler scheduler, HookRegistrar hooks) {
        Set<ServerPlayNetworkHandler> invalid = new HashSet<>();

        scheduler.interval(() -> tick(invalid), 1);

        hooks.registerHook(LanguageChangedCallback.HOOK, (player, lang, reason) -> update(player));
    }

    public synchronized void add(DynamicEntity entity) {
        Objects.requireNonNull(entity, "Dynamic entity cannot be null");
        entities.computeIfAbsent(entity, Tracker::new);
    }

    public synchronized void remove(DynamicEntity entity) {
        var tracker = entities.remove(entity);

        if (tracker == null) return;

        tracker.destroy();
    }

    public synchronized void clear() {
        var it = entities.entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();

            it.remove();

            entry.getValue().destroy();
        }
    }

    private synchronized void tick(Set<ServerPlayNetworkHandler> invalid) {
        for (var entry : entities.entrySet()) {  // maybe optimize in the future, e.g. make chunk-based to iterate less
            DynamicEntity dynamic = entry.getKey();
            var tracker = entry.getValue();

            tracker.tick();

            // mark all listeners as invalid initially
            invalid.addAll(tracker.byPlayer.keySet());

            // update player tracking status
            for (ServerPlayerEntity player : world.getPlayers()) {
                invalid.remove(player.networkHandler);

                double viewDistanceSquared = Math.pow(getViewDistance(player) * 16, 2);

                Vec3d position = dynamic.getPosition();
                int chunkX = ChunkSectionPos.getSectionCoord(position.getX());
                int chunkZ = ChunkSectionPos.getSectionCoord(position.getZ());

                boolean inRange = player.squaredDistanceTo(position) <= viewDistanceSquared
                        && isChunkTrackedBy(player, chunkX, chunkZ);

                if (inRange) {
                    tracker.add(player);
                } else {
                    tracker.remove(player);
                }
            }

            // cleanup invalid trackers that are no longer in the world
            for (ServerPlayNetworkHandler listener : invalid) {
                ServerPlayerEntity player = listener.getPlayer();

                if (player != null) {
                    tracker.remove(player);
                }
            }
        }
    }

    private synchronized void update(ServerPlayerEntity player) {
        for (Tracker tracker : entities.values()) {
            tracker.update(player);
        }
    }

    private int getViewDistance(ServerPlayerEntity player) {
        return MathHelper.clamp(player.getViewDistance(), 2, serverViewDistance);
    }

    // see ServerChunkLoadingManager
    private boolean isChunkTrackedBy(ServerPlayerEntity player, int chunkX, int chunkZ) {
        return player.getChunkFilter().isWithinDistance(chunkX, chunkZ) && !player.networkHandler.chunkDataSender.isInNextBatch(ChunkPos.toLong(chunkX, chunkZ));
    }

    /** A per-player tracking handler for a DynamicEntity instance */
    private class Tracker {

        private final DynamicEntity dynamic;

        /** Buffer for players who no longer track the entity and should be removed by ::tick */
        private final List<ServerPlayerEntity> removal = new ArrayList<>();

        /** Each player who tracks the dynamic entity is assigned a possibly shared Entity */
        private final Map<ServerPlayNetworkHandler, Entity> byPlayer = new HashMap<>();

        /** Each real entity is tracked by at least one player. Used to determine the players to which the associated tracker entry sends data. */
        private final Map<Entity, Set<ServerPlayNetworkHandler>> byEntity = new HashMap<>();

        /** Each entity has an associated tracker that gets updated during ::tick */
        private final Map<Entity, EntityTrackerEntry> trackerEntries = new HashMap<>();

        private Tracker(DynamicEntity dynamic) {
            this.dynamic = dynamic;
        }

        public void tick() {
            // gather entries where the entity was removed
            for (var entry : byPlayer.entrySet()) {
                Entity entity = entry.getValue();

                if (entity.isRemoved()) {
                    removal.add(entry.getKey().player);
                }
            }

            // actually remove players from this tracker
            for (var player : removal) {
                remove(player);
            }

            removal.clear();

            // tick entries
            for (EntityTrackerEntry entry : trackerEntries.values()) {
                entry.tick();
            }
        }

        public synchronized void destroy() {
            for (var entry : byPlayer.entrySet()) {
                ServerPlayerEntity player = entry.getKey().getPlayer();

                if (player == null) continue;

                removeEntityForPlayer(player, entry.getValue());
            }

            byPlayer.clear();
            byEntity.clear();
            trackerEntries.clear();
        }

        public synchronized void add(ServerPlayerEntity player) {
            if (byPlayer.containsKey(player.networkHandler)) return;

            Entity entity = dynamic.getEntity(player);

            if (entity == null || entity.isRemoved()) return;

            EntityTrackerEntry trackerEntry = getTrackerEntry(player, entity);

            byPlayer.put(player.networkHandler, entity);

            trackerEntry.startTracking(player);
        }

        public synchronized void remove(ServerPlayerEntity player) {
            var entity = byPlayer.remove(player.networkHandler);

            if (entity == null) return;

            removeEntityForPlayer(player, entity);
            removeListener(player, entity);
        }

        private void removeEntityForPlayer(ServerPlayerEntity player, Entity entity) {
            if (!player.isDisconnected() && player.getWorld() == entity.getWorld()) {
                EntityTrackerEntry trackerEntry = trackerEntries.get(entity);

                if (trackerEntry != null) {
                    trackerEntry.stopTracking(player);
                }
            }

            dynamic.cleanup(player);
        }

        private void removeListener(ServerPlayerEntity player, Entity entity) {
            var listeners = byEntity.get(entity);

            if (listeners == null) return;

            listeners.remove(player.networkHandler);

            if (!listeners.isEmpty()) return;

            // nobody tracks the entity; cleanup
            trackerEntries.remove(entity);
            byEntity.remove(entity);
        }

        public synchronized void update(ServerPlayerEntity player) {
            Entity current = byPlayer.get(player.networkHandler);

            if (current == null) return;

            Entity entity = dynamic.getEntity(player);

            if (Objects.equals(current, entity)) return;

            remove(player);
            add(player);
        }

        private EntityTrackerEntry getTrackerEntry(ServerPlayerEntity player, Entity entity) {
            var listeners = byEntity.computeIfAbsent(entity, e -> new HashSet<>());
            listeners.add(player.networkHandler);

            return trackerEntries.computeIfAbsent(entity, e -> {
                var type = e.getType();

                // use internal Minecraft class that is normally used for syncing the entity
                return new EntityTrackerEntry(world, e, type.getTrackTickInterval(), type.alwaysUpdateVelocity(), packet -> {
                    for (ServerPlayNetworkHandler tracker : listeners) {
                        tracker.sendPacket(packet);
                    }
                }, (packet, except) -> {
                    for (ServerPlayNetworkHandler tracker : listeners) {
                        ServerPlayerEntity p = tracker.getPlayer();

                        if (p != null && except.contains(p.getUuid())) continue;

                        tracker.sendPacket(packet);
                    }
                });
            });
        }
    }
}
