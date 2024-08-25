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
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;

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

    public void init(TaskScheduler scheduler) {
        Set<ServerPlayNetworkHandler> invalid = new HashSet<>();

        scheduler.interval(() -> tick(invalid), 1);
    }

    public void add(DynamicEntity entity) {
        entities.computeIfAbsent(entity, Tracker::new);
    }

    public void remove(DynamicEntity entity) {
        var tracker = entities.remove(entity);

        if (tracker == null) return;

        tracker.destroy();
    }

    private void tick(Set<ServerPlayNetworkHandler> invalid) {
        for (var entry : entities.entrySet()) {  // maybe optimize in the future
            DynamicEntity dynamic = entry.getKey();
            var tracker = entry.getValue();

            tracker.tick();

            // mark all listeners as invalid initially
            invalid.addAll(tracker.entriesByListener.keySet());

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

    private int getViewDistance(ServerPlayerEntity player) {
        return MathHelper.clamp(player.getViewDistance(), 2, serverViewDistance);
    }

    // see ServerChunkLoadingManager
    private boolean isChunkTrackedBy(ServerPlayerEntity player, int chunkX, int chunkZ) {
        return player.getChunkFilter().isWithinDistance(chunkX, chunkZ) && !player.networkHandler.chunkDataSender.isInNextBatch(ChunkPos.toLong(chunkX, chunkZ));
    }

    private class Tracker {

        private final DynamicEntity dynamic;
        private final Map<ServerPlayNetworkHandler, EntityPair> entriesByListener = new HashMap<>();
        private final Map<Entity, Set<ServerPlayNetworkHandler>> listenersByEntity = new HashMap<>();
        private final Map<Entity, EntityTrackerEntry> entriesByEntity = new HashMap<>();
        private final Set<EntityTrackerEntry> entries = new HashSet<>();

        private Tracker(DynamicEntity dynamic) {
            this.dynamic = dynamic;
        }

        public void tick() {
            for (EntityTrackerEntry entry : entries) {
                entry.tick();
            }
        }

        public synchronized void destroy() {
            for (var entry : entriesByListener.entrySet()) {
                ServerPlayerEntity player = entry.getKey().getPlayer();

                if (player == null) continue;

                removeEntityForPlayer(player, entry.getValue());
            }

            entriesByListener.clear();
            listenersByEntity.clear();
            entriesByEntity.clear();
            entries.clear();
        }

        public synchronized void add(ServerPlayerEntity player) {
            if (entriesByListener.containsKey(player.networkHandler)) return;

            Entity entity = dynamic.getEntity(player);
            EntityTrackerEntry trackerEntry = getTrackerEntry(player, entity);

            entriesByListener.put(player.networkHandler, new EntityPair(entity, trackerEntry));

            trackerEntry.startTracking(player);
        }

        public synchronized void remove(ServerPlayerEntity player) {
            var pair = entriesByListener.remove(player.networkHandler);

            if (pair == null) return;

            removeListener(player, pair.entity());

            removeEntityForPlayer(player, pair);
        }

        private void removeEntityForPlayer(ServerPlayerEntity player, EntityPair pair) {
            if (!player.isDisconnected() && player.getWorld() == pair.entity().getWorld()) {
                pair.trackerEntry().stopTracking(player);
            }

            dynamic.cleanup(player);
        }

        private void removeListener(ServerPlayerEntity player, Entity entity) {
            var listeners = listenersByEntity.get(entity);

            if (listeners == null) return;

            listeners.remove(player.networkHandler);

            if (!listeners.isEmpty()) return;

            // cleanup entity
            EntityTrackerEntry trackerEntry = entriesByEntity.remove(entity);

            if (trackerEntry == null) return;

            entries.remove(trackerEntry);
        }

        private EntityTrackerEntry getTrackerEntry(ServerPlayerEntity player, Entity entity) {
            // get or create listener set for the entity
            var listeners = listenersByEntity.computeIfAbsent(entity, e -> new HashSet<>());
            listeners.add(player.networkHandler);

            // get or create tracker entry for the entity
            return entriesByEntity.computeIfAbsent(entity, e -> {
                var type = e.getType();

                // use internal Minecraft class that is normally used for syncing the entity
                var entry = new EntityTrackerEntry(world, e, type.getTrackTickInterval(), type.alwaysUpdateVelocity(), packet -> {
                    for (ServerPlayNetworkHandler tracker : listeners) {
                        tracker.sendPacket(packet);
                    }
                });

                entries.add(entry);

                return entry;
            });
        }
    }

    private record EntityPair(Entity entity, EntityTrackerEntry trackerEntry) {}
}
