package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.kibu.mc.BuiltinKibuBlockState;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;

public class JumpRoom {

    private final float value;
    private final BlockStructure structure;
    private final BlockBox bounds;
    private final Connectors connectors;
    private final JumpAssistance assistance;
    private final List<Checkpoint> checkpoints;

    public JumpRoom(float value, BlockStructure structure, BlockBox bounds, Connectors connectors,
                    JumpAssistance assistance, List<Checkpoint> checkpoints) {
        this.value = value;
        this.structure = structure;
        this.bounds = bounds;
        this.connectors = connectors;
        this.assistance = assistance;
        this.checkpoints = checkpoints;
    }

    public float getValue() {
        return value;
    }

    public BlockBox getBounds() {
        return bounds;
    }

    public BlockStructure getStructure() {
        return structure;
    }

    public Connectors getConnectors() {
        return connectors;
    }

    public boolean isStraight() {
        return connectors.entrance().direction() == connectors.exit().direction();
    }

    public static Partial from(BlockStructure structure) {
        BlockBox bounds = StructureUtil.getBounds(structure);

        Connectors connectors = findConnectors(structure, bounds);

        return (value, assistance, checkpoints) -> {
            var orig = structure.getOrigin();
            Vec3i origin = new Vec3i(orig.getX(), orig.getY(), orig.getZ());

            var relativeAssistance = assistance.relativize(origin);

            var relativeCheckpoints = checkpoints.stream()
                    .map(checkpoint -> checkpoint.relativize(origin))
                    .toList();

            return new JumpRoom(value, structure, bounds, connectors, relativeAssistance, relativeCheckpoints);
        };
    }

    @NotNull
    private static JumpRoom.Connectors findConnectors(BlockStructure structure, BlockBox bounds) {
        Connector entry = null, exit = null;

        final var origin = structure.getOrigin();
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (var pos : structure.getBlockPositions()) {
            int x = pos.getX() - ox, y = pos.getY() - oy, z = pos.getZ() - oz;

            Direction dir = bounds.getTangentSurface(x, y, z);
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

        if (entry == null) throw new IllegalStateException("Entry not found");
        if (exit == null) throw new IllegalStateException("Exit not found");

        BlockPos entryPos = entry.pos(), exitPos = exit.pos();

        structure.setBlockState(new KibuBlockPos(
                entryPos.getX() + ox,
                entryPos.getY() + oy,
                entryPos.getZ() + oz
        ), BuiltinKibuBlockState.AIR);

        structure.setBlockState(new KibuBlockPos(
                exitPos.getX() + ox,
                exitPos.getY() + oy,
                exitPos.getZ() + oz
        ), BuiltinKibuBlockState.AIR);

        return new Connectors(entry, exit);
    }

    public RoomData createData() {
        return new RoomData(value, assistance, checkpoints);
    }

    public record Connectors(Connector entrance, Connector exit) {}

    public interface Partial {
        JumpRoom with(float value, JumpAssistance assistance, List<Checkpoint> checkpoints);
    }
}
