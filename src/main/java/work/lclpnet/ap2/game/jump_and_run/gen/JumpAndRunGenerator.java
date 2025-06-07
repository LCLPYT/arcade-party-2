package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

import static java.lang.Math.acos;
import static java.lang.Math.toDegrees;
import static java.util.Objects.requireNonNull;
import static net.minecraft.block.HorizontalFacingBlock.FACING;

public class JumpAndRunGenerator {

    private final float targetMinutes;
    private final Random random;
    private final Logger logger;

    public JumpAndRunGenerator(float targetMinutes, Random random, Logger logger) {
        this.targetMinutes = targetMinutes;
        this.random = random;
        this.logger = logger;
    }

    public JumpAndRun generate(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        Direction stackingDir = Direction.SOUTH;

        List<JumpRoom> rooms = new ArrayList<>(parts.rooms());
        Collections.shuffle(rooms, random);

        List<Segment> segments = new ArrayList<>();
        float minutes = 0;

        while (minutes < targetMinutes && !rooms.isEmpty()) {
            JumpRoom room = rooms.removeFirst();

            SegmentInfo segment = createSegmentParts(room, parts, stackingDir);

            if (segment == null) {
                logger.warn("Segment for room {} could not be created, skipping it", room.id());
                continue;
            }

            segment = segment.offset(spawnPos);

            BlockBox bounds = segment.bounds();

            segments.add(new Segment(segment.parts, bounds, segment.checkpoints(), segment.roomInfo, segment.start));

            minutes += room.estimatedMinutes();

            spawnPos = spawnPos.offset(stackingDir, bounds.sideLength(stackingDir.getAxis()));
        }

        return new JumpAndRun(segments);
    }

    @Nullable
    private SegmentInfo createSegmentParts(JumpRoom room, JumpAndRunSetup.Parts parts, Direction stackingDir) {
        List<JumpPart> segment = new ArrayList<>(5);

        var entrance = room.connectors().entrance();
        var exit = room.connectors().exit();

        OrientedPart mainPart;
        JumpRoom.Start start;
        Checkpoint end;

        // Previously, all the rooms were interconnected. Now, rooms are independent of each other.
        // Older rooms required both a start and an end connector, on which the bridges to the next rooms were placed.
        // In the modern version, a room can either have both start and end connectors, only the start connector,
        // only the end connector, or neither.
        // Open connectors are connected with the template start or end room of each segment.

        if (entrance != null) {
            BlockPos spawnPos = BlockPos.ORIGIN;

            OrientedPart startPart = createStart(parts.start(), spawnPos, stackingDir);
            segment.add(startPart);

            Connector startConnector = requireNonNull(startPart.out());
            Bridge startBridge = makeBridge(startConnector);
            segment.add(startBridge);

            mainPart = createMainPart(room, startConnector, entrance);

            double dot = stackingDir.getDoubleVector().dotProduct(Direction.SOUTH.getDoubleVector());
            float spawnYaw = (float) toDegrees(acos(dot));
            start = new JumpRoom.Start(spawnPos, spawnYaw, startBridge.bounds());
        } else {
            start = room.start().orElse(null);

            if (start == null) {
                logger.error("Room {} without entrance must define a start", room.id());
                return null;
            }

            mainPart = createMainPart(room, exit);
        }

        segment.add(mainPart);

        if (exit != null) {
            Connector endConnector = requireNonNull(mainPart.out());
            Bridge endBridge = makeBridge(endConnector);
            segment.add(endBridge);

            OrientedPart endPart = createEnd(parts.end(), endConnector);
            segment.add(endPart);

            end = endBridge.asCheckpoint();
        } else {
            end = room.end().orElse(null);

            if (end == null) {
                logger.error("Room {} without exit must define an end", room.id());
                return null;
            }
        }

        return new SegmentInfo(segment, mainPart.info(), start, end);
    }

    private OrientedPart createMainPart(JumpRoom room, @Nullable Connector exit) {
        RoomData data = room.createData();

        return OrientedPart.createTransformed(room.structure(), BlockPos.ORIGIN, 0, null, exit, data);
    }

    private static OrientedPart createMainPart(JumpRoom room, Connector connector, Connector entrance) {
        Direction direction = connector.direction();
        BlockPos pos = connector.pos();

        int targetRotation = direction.getOpposite().getHorizontalQuarterTurns();
        int rotation = entrance.direction().getHorizontalQuarterTurns() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos entrancePos = entrance.pos();
        Vec3i rotatedEntrance = rotationMatrix.transform(entrancePos);

        Vec3i bridgeOffset = direction.getVector().multiply(2);
        BlockPos entranceOffset = pos.subtract(rotatedEntrance).add(bridgeOffset);

        RoomData data = room.createData();

        return OrientedPart.createTransformed(room.structure(), entranceOffset, rotation, entrance, room.connectors().exit(), data);
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

        return OrientedPart.createTransformed(startStruct, startOffset, rotation, null, startRoom.exit(), null);
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

        return OrientedPart.createTransformed(endStruct, endOffset, rotation, null, exit, null);
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

        return new Bridge(structure, bounds, pos.down().add(vec), dir, BlockPos.ORIGIN);
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

    private record SegmentInfo(List<JumpPart> parts, RoomInfo roomInfo, JumpRoom.Start start, Checkpoint end) {

        public BlockBox bounds() {
            return BlockBox.enclosing(parts.stream().map(JumpPart::bounds).toList());
        }

        public List<Checkpoint> checkpoints() {
            List<Checkpoint> checkpoints = new ArrayList<>();

            checkpoints.add(start.checkpoint());

            for (JumpPart part : parts) {
                if (!(part instanceof OrientedPart p)) continue;

                RoomData data = p.info().data();

                if (data == null) continue;

                checkpoints.addAll(data.checkpoints());
            }

            checkpoints.add(end);

            return checkpoints;
        }

        public SegmentInfo offset(BlockPos offset) {
            List<JumpPart> parts = this.parts.stream().map(part -> part.transform(offset)).toList();

            var mat = AffineIntMatrix.makeTranslation(offset);

            BlockPos invOffset = offset.multiply(-1);

            JumpRoom.Start relStart = start.relativize(invOffset);
            Checkpoint relEnd = end.relativize(invOffset);

            RoomInfo transformedInfo = roomInfo.transform(mat);

            return new SegmentInfo(parts, transformedInfo, relStart, relEnd);
        }
    }
}
