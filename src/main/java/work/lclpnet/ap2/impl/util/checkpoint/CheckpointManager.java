package work.lclpnet.ap2.impl.util.checkpoint;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.api.util.collision.MovementObserver;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.*;

public class CheckpointManager {

    private static final boolean DEBUG_CHECKPOINTS = false;

    @Getter
    private final List<Checkpoint> checkpoints;
    private final Object2IntMap<Checkpoint> checkpointIndices;
    private final Map<UUID, Checkpoint> playerCheckpoints = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private DebugController debugController;

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
        return playerCheckpoints.computeIfAbsent(player.getUuid(), uuid -> checkpoints.getFirst());
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

    public void init(CollisionDetector collisionDetector, MovementObserver movementObserver, ServerWorld world) {
        for (int i = 0, len = checkpoints.size(); i < len; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            Collider bounds = checkpoint.bounds();

            collisionDetector.add(bounds);

            int index = i;

            movementObserver.whenEntering(bounds, player -> onEnterCheckpoint(player, index));
        }

        debugController = new DebugController();

        if (!ApConstants.DEBUG || !DEBUG_CHECKPOINTS) return;

        debugController.init(ApResources.getInstance(), world);

        debugController.renderer().ifPresent(renderer -> {
            for (Checkpoint checkpoint : checkpoints) {
                renderer.box(checkpoint.bounds(), Blocks.GREEN_STAINED_GLASS.getDefaultState());

                Vec3d pos = checkpoint.pos().toBottomCenterPos();

                renderer.marker(pos, Blocks.GREEN_CONCRETE.getDefaultState(), 0x00ff00);
                renderer.arrow(pos, MathUtil.yaw2vec(checkpoint.yaw()), 0.25, Blocks.GREEN_WOOL.getDefaultState());
            }
        });
    }

    private void onEnterCheckpoint(ServerPlayerEntity player, int index) {
        if (!grantCheckpoint(player, index)) return;

        listeners.forEach(listener -> listener.accept(player, index));
    }

    public void whenCheckpointReached(Listener action) {
        listeners.add(Objects.requireNonNull(action));
    }

    public void destroy() {
        debugController.destroy();
    }

    public interface Listener {
        void accept(ServerPlayerEntity player, int checkpoint);
    }
}
