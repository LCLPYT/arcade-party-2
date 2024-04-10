package work.lclpnet.ap2.impl.util.checkpoint;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.api.util.collision.MovementObserver;

import java.util.*;

public class CheckpointManager {

    private final List<Checkpoint> checkpoints;
    private final Object2IntMap<Checkpoint> checkpointIndices;
    private final Map<UUID, Checkpoint> playerCheckpoints = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public CheckpointManager(List<Checkpoint> checkpoints) {
        if (checkpoints.isEmpty()) throw new IllegalStateException("Checkpoints must not be empty");
        this.checkpoints = Collections.unmodifiableList(checkpoints);
        this.checkpointIndices = new Object2IntOpenHashMap<>(checkpoints.size());

        for (int i = 0, size = checkpoints.size(); i < size; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            checkpointIndices.put(checkpoint, i);
        }
    }

    public Checkpoint getCheckpoint(ServerPlayerEntity player) {
        return playerCheckpoints.computeIfAbsent(player.getUuid(), uuid -> checkpoints.get(0));
    }

    public boolean grantCheckpoint(ServerPlayerEntity player, int grant) {
        grant = MathHelper.clamp(grant, 0, checkpoints.size() - 1);

        UUID uuid = player.getUuid();
        Checkpoint current = playerCheckpoints.get(uuid);

        if (current != null && checkpointIndices.getInt(current) >= grant) return false;

        Checkpoint checkpoint = checkpoints.get(grant);
        playerCheckpoints.put(uuid, checkpoint);

        return grant > 0;
    }

    public void init(CollisionDetector collisionDetector, MovementObserver movementObserver) {
        for (int i = 0, checkpointsSize = checkpoints.size(); i < checkpointsSize; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            Collider checkpointBounds = checkpoint.bounds();

            collisionDetector.add(checkpointBounds);

            int index = i;

            movementObserver.whenEntering(checkpointBounds, player -> onEnterCheckpoint(player, index));
        }
    }

    private void onEnterCheckpoint(ServerPlayerEntity player, int index) {
        if (!grantCheckpoint(player, index)) return;

        listeners.forEach(listener -> listener.accept(player, index));
    }

    public void whenCheckpointReached(Listener action) {
        listeners.add(Objects.requireNonNull(action));
    }

    public interface Listener {
        void accept(ServerPlayerEntity player, int checkpoint);
    }
}
