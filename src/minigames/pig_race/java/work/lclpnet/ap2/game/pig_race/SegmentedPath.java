package work.lclpnet.ap2.game.pig_race;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.movement.TickMovementObserver;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.Collider;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;

import static java.lang.Math.ceil;

public class SegmentedPath {

    public static final boolean DEBUG_PROGRESS = false;

    private final List<Segment> segments;
    private final Object2IntMap<UUID> playerSegments = new Object2IntOpenHashMap<>();
    @Getter
    private final double combinedLength;

    private SegmentedPath(List<Segment> segments) {
        if (segments.isEmpty()) throw new IllegalArgumentException("At least one marker is required");

        this.segments = segments;
        combinedLength = segments.stream().mapToDouble(segment -> segment.path().getLength()).sum();
    }

    public void init(Participants participants, TaskScheduler scheduler, HookRegistrar hooks, MinecraftServer server,
                     DebugController debugController) {

        var movementObserver = new TickMovementObserver(new ChunkedCollisionDetector(), participants::isParticipating);
        movementObserver.init(scheduler, hooks, server);

        for (Segment segment : segments) {
            movementObserver.whenEntering(segment.marker(), player -> onReachSegmentMarker(player, segment));
        }

        if (DEBUG_PROGRESS) {
            debugSegments(participants, scheduler, debugController);
        }
    }

    private void debugSegments(Participants participants, TaskScheduler scheduler, DebugController debugController) {
        Block[] colors = new Block[] {
                Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.RED_CONCRETE,
                Blocks.ORANGE_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.CYAN_CONCRETE
        };

        for (Segment segment : segments) {
            var debugger = new SplinePathDebugger(debugController, segment.path);

            BlockState pathColor = colors[segment.index() % colors.length].getDefaultState();
            debugger.renderPath((int) Math.ceil(1000 * segment.relativeLength()), pathColor);

            debugger.renderLiveProgress(() -> participants, scheduler, entity -> {
                if (!(entity instanceof ServerPlayerEntity player)) return -1;

                return getSegmentIndex(player) == segment.index()
                        ? DyeColor.LIME.getEntityColor()
                        : DyeColor.RED.getEntityColor();
            });
        }
    }

    private void onReachSegmentMarker(ServerPlayerEntity player, Segment segment) {
        int nextSegmentIndex = (getSegment(player).index() + 1) % segments.size();

        if (segment.index() != nextSegmentIndex) return;

        playerSegments.put(player.getUuid(), nextSegmentIndex);
    }

    public Segment getSegment(ServerPlayerEntity player) {
        return segments.get(getSegmentIndex(player));
    }

    private int getSegmentIndex(ServerPlayerEntity player) {
        return playerSegments.getOrDefault(player.getUuid(), 0);
    }

    public double getProgress(ServerPlayerEntity player) {
        Segment segment = getSegment(player);
        double relativeProgress = segment.path().getProgress(player.getPos());

        return segment.marker().progress() + relativeProgress * segment.relativeLength();
    }

    public static SegmentedPath create(SplinePath path, List<Checkpoint> checkpoints, Logger logger) {
        record UnorderedMarker(BlockBox box, double progress) {}

        var boxes = checkpoints.stream()
                .map(checkpoint -> {
                    double progress = path.getProgress(checkpoint.pos());

                    return new UnorderedMarker(checkpoint.bounds(), progress);
                })
                .sorted(Comparator.comparing(UnorderedMarker::progress))
                .toList();

        List<Segment> segments = new ArrayList<>();

        for (int i = 0, boxesSize = boxes.size(); i < boxesSize; i++) {
            UnorderedMarker box = boxes.get(i);
            var marker = new Marker(box.box, box.progress);

            double from = box.progress();
            double to = i == boxes.size() - 1 ? 1.0 : boxes.get(i + 1).progress();

            var subpath = subpath(path, from, to, logger)
                    .orElseThrow(() -> new IllegalStateException("Failed to create subpath for segmented path"));

            segments.add(new Segment(subpath, marker, i, to - from));
        }

        return new SegmentedPath(segments);
    }

    public static Optional<SplinePath> subpath(SplinePath path, double from, double to, Logger logger) {
        final int totalSamples = 100;

        double relativeLength = to - from;
        final int samples = (int) ceil(totalSamples * relativeLength);

        List<Vec3d> keypoints = new ArrayList<>(samples);

        for (int i = 0; i < samples; i++) {
            Vec3d pos = path.samplePosition(from + i * relativeLength / (samples - 1));
            keypoints.add(pos);
        }

        return SplinePath.create(keypoints, logger);
    }

    public record Segment(SplinePath path, Marker marker, int index, double relativeLength) {}

    public record Marker(BlockBox box, double progress) implements Collider {

        @Override
        public boolean collidesWith(double x, double y, double z) {
            return box.collidesWith(x, y, z);
        }

        @Override
        public boolean collidesWith(Box box) {
            return this.box.collidesWith(box);
        }

        @Override
        public BlockPos min() {
            return box.min();
        }

        @Override
        public BlockPos max() {
            return box.max();
        }
    }
}
