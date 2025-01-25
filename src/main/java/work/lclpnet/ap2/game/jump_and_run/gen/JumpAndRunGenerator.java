package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.minecraft.block.HorizontalFacingBlock.FACING;

public class JumpAndRunGenerator {

    private final float targetMinutes;
    private final Random random;

    public JumpAndRunGenerator(float targetMinutes, Random random) {
        this.targetMinutes = targetMinutes;
        this.random = random;
    }

    public JumpAndRun generate(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        Direction stackingDir = Direction.SOUTH;

        List<JumpRoom> rooms = new ArrayList<>(parts.rooms());
        List<Segment> segments = new ArrayList<>();
        float minutes = 0;

        while (minutes < targetMinutes && !rooms.isEmpty()) {
            JumpRoom room = rooms.remove(random.nextInt(rooms.size()));

            OrientedPart start = createStart(parts.start(), spawnPos, stackingDir);
            Connector startConnector = requireNonNull(start.out());
            OrientedPart mainPart = createPart(room, startConnector);
            Connector endConnector = requireNonNull(mainPart.out());
            OrientedPart end = createEnd(parts.end(), endConnector);

            Bridge startBridge = makeBridge(startConnector);
            Bridge endBridge = makeBridge(endConnector);

            var jumpParts = List.of(start, startBridge, mainPart, endBridge, end);
            var bounds = BlockBox.enclosing(jumpParts.stream().map(JumpPart::bounds).toList());

            List<Checkpoint> checkpoints = jumpParts.stream()
                    .flatMap(part -> switch (part) {
                        case Bridge bridge -> Stream.of(bridge.asCheckpoint());
                        case OrientedPart p when p.info().data() != null -> p.info().data().checkpoints().stream();
                        default -> Stream.empty();
                    })
                    .filter(Objects::nonNull)
                    .toList();

            segments.add(new Segment(jumpParts, bounds, checkpoints, startBridge.bounds(), mainPart.info(), spawnPos, 0f));

            minutes += room.estimatedMinutes();

            spawnPos = spawnPos.offset(stackingDir, bounds.length());
        }

        return new JumpAndRun(segments);
    }

    private static OrientedPart createPart(JumpRoom room, Connector connector) {
        Direction direction = connector.direction();
        BlockPos pos = connector.pos();

        JumpRoom.Connectors connectors = room.connectors();
        Connector entrance = connectors.entrance();

        int targetRotation = direction.getOpposite().getHorizontalQuarterTurns();
        int rotation = entrance.direction().getHorizontalQuarterTurns() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos entrancePos = entrance.pos();
        Vec3i rotatedEntrance = rotationMatrix.transform(entrancePos);

        Vec3i bridgeOffset = direction.getVector().multiply(2);
        BlockPos entranceOffset = pos.subtract(rotatedEntrance).add(bridgeOffset);

        RoomData data = room.createData();

        return new OrientedPart(room.structure(), entranceOffset, rotation, entrance, connectors.exit(), data);
    }

    @NotNull
    private OrientedPart createStart(JumpEnd startRoom, BlockPos spawnPos, Direction facing) {
        BlockStructure startStruct = startRoom.structure();

        int targetRotation = facing.getHorizontalQuarterTurns();
        int rotation = startRoom.exit().direction().getHorizontalQuarterTurns() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos spawn = requireNonNull(startRoom.spawn(), "Spawn position must be non-null");
        Vec3i rotatedSpawn = rotationMatrix.transform(spawn);
        BlockPos startOffset = spawnPos.subtract(rotatedSpawn);

        return new OrientedPart(startStruct, startOffset, rotation, null, startRoom.exit(), null);
    }

    @NotNull
    private OrientedPart createEnd(JumpEnd endRoom, Connector connector) {
        BlockStructure endStruct = endRoom.structure();

        Connector exit = endRoom.exit();

        int targetRotation = connector.direction().getOpposite().getHorizontalQuarterTurns();
        int rotation = exit.direction().getHorizontalQuarterTurns() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos exitPos = exit.pos();
        Vec3i rotatedExit = rotationMatrix.transform(exitPos);

        Vec3i bridgeOffset = connector.direction().getVector().multiply(2);
        BlockPos endOffset = connector.pos().subtract(rotatedExit).add(bridgeOffset);

        return new OrientedPart(endStruct, endOffset, rotation, null, exit, null);
    }

    @NotNull
    private Bridge makeBridge(Connector connector) {
        BlockPos pos = connector.pos();
        Direction dir = connector.direction();
        Vec3i vec = dir.getVector();
        Vec3i up = Direction.UP.getVector();
        Vec3i side = vec.crossProduct(up);  // up and vec are orthogonal and unit, thus right is a unit vector

        BlockState base = Blocks.MAGENTA_GLAZED_TERRACOTTA.getDefaultState();
        BlockState upState = base.with(FACING, dir);
        BlockState downState = base.with(FACING, dir.getOpposite());
        BlockState sideState = base.with(FACING, dir.rotateYCounterclockwise());

        Vec3i up2 = up.multiply(2);
        Vec3i side2 = side.multiply(2);
        BlockPos bridgePos = pos.add(vec);

        Map<BlockPos, BlockState> blocks = new HashMap<>();

        buildAxis(bridgePos, side, up2, upState, downState, blocks);
        buildAxis(bridgePos, up, side2, sideState, sideState, blocks);

        BlockStructure structure = makeStructure(blocks);
        BlockBox bounds = getBridgeBounds(connector);

        return new Bridge(structure, bounds, pos.down().add(vec), dir);
    }

    @NotNull
    private static BlockBox getBridgeBounds(Connector connector) {
        Vec3i vec = connector.direction().getVector();
        Vec3i up = Direction.UP.getVector();
        Vec3i side = vec.crossProduct(up);  // up and vec are orthogonal and unit, thus right is a unit vector

        Vec3i up2 = up.multiply(2);
        Vec3i side2 = side.multiply(2);
        BlockPos bridgePos = connector.pos().add(vec);

        return new BlockBox(bridgePos.add(up2).add(side2), bridgePos.subtract(up2).subtract(side2));
    }

    private void buildAxis(BlockPos pos, Vec3i side, Vec3i axis, BlockState upState, BlockState downState, Map<BlockPos, BlockState> blocks) {
        blocks.put(pos.add(axis), upState);
        blocks.put(pos.add(axis).add(side), upState);
        blocks.put(pos.add(axis).subtract(side), upState);
        blocks.put(pos.subtract(axis), downState);
        blocks.put(pos.subtract(axis).add(side), downState);
        blocks.put(pos.subtract(axis).subtract(side), downState);
    }

    private BlockStructure makeStructure(Map<BlockPos, BlockState> blocks) {
        SimpleBlockStructure structure = FabricStructureWrapper.createSimpleStructure();
        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        blocks.forEach((pos, state) -> structure.setBlockState(adapter.adapt(pos), adapter.adapt(state)));
        return structure;
    }
}
