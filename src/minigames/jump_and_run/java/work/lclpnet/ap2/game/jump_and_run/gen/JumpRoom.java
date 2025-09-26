package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.mc.BuiltinKibuBlockState;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JumpRoom {

    private final MetaData metaData;
    private final BlockStructure structure;
    private final BlockBox bounds;
    private final Connectors connectors;
    private final JumpAssistance assistance;
    private final List<Checkpoint> checkpoints;
    private final @Nullable Start start;
    private final @Nullable Checkpoint end;
    private final String id;

    public JumpRoom(MetaData metaData, BlockStructure structure, BlockBox bounds, Connectors connectors,
                    JumpAssistance assistance, List<Checkpoint> checkpoints, @Nullable Start start, @Nullable Checkpoint end, String id) {
        this.metaData = metaData;
        this.structure = structure;
        this.bounds = bounds;
        this.connectors = connectors;
        this.assistance = assistance;
        this.checkpoints = checkpoints;
        this.start = start;
        this.end = end;
        this.id = id;
    }

    public MetaData metaData() {
        return metaData;
    }

    public BlockBox bounds() {
        return bounds;
    }

    public BlockStructure structure() {
        return structure;
    }

    public Connectors connectors() {
        return connectors;
    }

    public Optional<Start> start() {
        return Optional.ofNullable(start);
    }

    public Optional<Checkpoint> end() {
        return Optional.ofNullable(end);
    }

    public String id() {
        return id;
    }

    @NotNull
    private static JumpRoom.Connectors findConnectors(BlockStructure structure, BlockBox bounds) {
        @Nullable Connector entry = null, exit = null;

        final var origin = structure.getOrigin();
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (var pos : structure.getBlockPositions()) {
            int x = pos.getX() - ox, y = pos.getY() - oy, z = pos.getZ() - oz;

            Direction dir = bounds.tangentSurface(x, y, z);
            if (dir == null || dir.getAxis() == Direction.Axis.Y) continue;

            var state = structure.getBlockState(pos);
            String mat = state.getAsString();

            if (mat.startsWith("minecraft:command_block")) {
                entry = new Connector(new BlockPos(x, y, z), dir);

                if (exit != null) break;
            } else if (mat.startsWith("minecraft:chain_command_block")) {
                exit = new Connector(new BlockPos(x, y, z), dir);

                if (entry != null) break;
            }
        }

        if (entry != null) {
            BlockPos entryPos = entry.pos();

            structure.setBlockState(new KibuBlockPos(
                    entryPos.getX() + ox,
                    entryPos.getY() + oy,
                    entryPos.getZ() + oz
            ), BuiltinKibuBlockState.AIR);
        }

        if (exit != null) {
            BlockPos exitPos = exit.pos();

            structure.setBlockState(new KibuBlockPos(
                    exitPos.getX() + ox,
                    exitPos.getY() + oy,
                    exitPos.getZ() + oz
            ), BuiltinKibuBlockState.AIR);
        }

        return new Connectors(entry, exit);
    }

    public RoomData createData() {
        return new RoomData(metaData.estimatedMinutes(), assistance, checkpoints);
    }

    public record Connectors(@Nullable Connector entrance, @Nullable Connector exit) {}

    public static class Start {
        private final Checkpoint checkpoint;
        private final List<BlockBox> gate;

        public Start(BlockPos spawn, float spawnYaw, BlockBox bounds, List<BlockBox> extraBounds) {
            this(new Checkpoint(spawn, spawnYaw, bounds), extraBounds);
        }

        public Start(Checkpoint checkpoint, List<BlockBox> extraBounds) {
            this.checkpoint = checkpoint;
            this.gate = extraBounds;
        }

        public BlockPos spawn() {
            return checkpoint.pos();
        }

        public float spawnYaw() {
            return checkpoint.yaw();
        }

        public List<BlockBox> gate() {
            return gate;
        }

        public Checkpoint checkpoint() {
            return checkpoint;
        }

        public Start relativize(Vec3i origin) {
            Vec3i translation = origin.multiply(-1);

            List<BlockBox> relativeBounds = gate.stream()
                    .map(box -> box.translate(translation))
                    .toList();

            return new Start(checkpoint.relativize(origin), relativeBounds);
        }
    }

    public record MetaData(float estimatedMinutes, int stackingMargin, float weight, Set<ApEffect> effects) {}

    public interface Partial {
        static Partial from(BlockStructure structure, String id) {
            BlockBox bounds = StructureUtil.getBounds(structure);

            Connectors connectors = findConnectors(structure, bounds);

            return (metaData, assistance, checkpoints, start, end) -> {
                var orig = structure.getOrigin();
                Vec3i origin = new Vec3i(orig.getX(), orig.getY(), orig.getZ());

                var relativeAssistance = assistance.relativize(origin);

                var relativeCheckpoints = checkpoints.stream()
                        .map(checkpoint -> checkpoint.relativize(origin))
                        .toList();

                var relStart = start != null ? start.relativize(origin) : null;
                var relEnd = end != null ? end.relativize(origin) : null;

                return new JumpRoom(metaData, structure, bounds, connectors, relativeAssistance, relativeCheckpoints,
                        relStart, relEnd, id);
            };
        }

        JumpRoom with(MetaData metaData, JumpAssistance assistance, List<Checkpoint> checkpoints,
                      @Nullable Start start, @Nullable Checkpoint end);
    }
}
