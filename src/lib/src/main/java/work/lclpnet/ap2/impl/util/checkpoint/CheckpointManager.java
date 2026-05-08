package work.lclpnet.ap2.impl.util.checkpoint;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.MovementObserver;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.Collider;

import java.util.*;

public class CheckpointManager {

    private static final boolean DEBUG_CHECKPOINTS = false;

    @Getter
    private final List<Checkpoint> checkpoints;
    private final Object2IntMap<Checkpoint> checkpointIndices;
    private final Map<UUID, Checkpoint> playerCheckpoints = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final DebugController debugController;

    public CheckpointManager(List<Checkpoint> checkpoints, DebugController debugController) {
        if (checkpoints.isEmpty()) throw new IllegalStateException("Checkpoints must not be empty");
        this.checkpoints = Collections.unmodifiableList(checkpoints);
        this.checkpointIndices = new Object2IntOpenHashMap<>(checkpoints.size());
        this.debugController = debugController;

        for (int i = 0, size = checkpoints.size(); i < size; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            checkpointIndices.put(checkpoint, i);
        }
    }

    public Checkpoint getCheckpoint(ServerPlayer player) {
        return playerCheckpoints.computeIfAbsent(player.getUUID(), _ -> checkpoints.getFirst());
    }

    public boolean grantCheckpoint(ServerPlayer player, int grant) {
        grant = Mth.clamp(grant, 0, checkpoints.size() - 1);

        UUID uuid = player.getUUID();
        Checkpoint current = playerCheckpoints.get(uuid);

        if (current != null && checkpointIndices.getInt(current) >= grant) return false;

        Checkpoint checkpoint = checkpoints.get(grant);
        playerCheckpoints.put(uuid, checkpoint);

        return grant > 0;
    }

    public void init(CollisionDetector collisionDetector, MovementObserver movementObserver, ServerLevel world) {
        for (int i = 0, len = checkpoints.size(); i < len; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            Collider bounds = checkpoint.bounds();

            collisionDetector.add(bounds);

            int index = i;

            movementObserver.whenEntering(bounds, player -> onEnterCheckpoint(player, index));
        }

        if (!DEBUG_CHECKPOINTS) return;

        debugController.renderer().ifPresent(renderer -> {
            for (Checkpoint checkpoint : checkpoints) {
                renderer.box(checkpoint.bounds(), Blocks.GREEN_STAINED_GLASS.defaultBlockState());

                Vec3 pos = checkpoint.pos();

                renderer.marker(pos, Blocks.GREEN_CONCRETE.defaultBlockState(), 0x00ff00);
                renderer.arrow(pos, MathUtil.yaw2vec(checkpoint.yaw()), 0.25, Blocks.GREEN_WOOL.defaultBlockState());
            }
        });
    }

    private void onEnterCheckpoint(ServerPlayer player, int index) {
        if (!grantCheckpoint(player, index)) return;

        listeners.forEach(listener -> listener.accept(player, index));
    }

    public void whenCheckpointReached(Listener action) {
        listeners.add(Objects.requireNonNull(action));
    }

    public void destroy() {
        debugController.destroy();
    }

    public void resetCheckpoints(ServerPlayer player) {
        playerCheckpoints.put(player.getUUID(), checkpoints.getFirst());
    }

    public interface Listener {
        void accept(ServerPlayer player, int checkpoint);
    }
}
