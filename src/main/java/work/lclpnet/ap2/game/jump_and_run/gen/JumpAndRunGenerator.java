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
import work.lclpnet.ap2.impl.util.StructurePrintable;
import work.lclpnet.ap2.impl.util.collision.BoxCollisionDetector;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.block.HorizontalFacingBlock.FACING;

public class JumpAndRunGenerator {

    private final float maxTotal;
    private final Random random;
    private final Logger logger;
    private final int minHeight, maxHeight;

    public JumpAndRunGenerator(float maxTotal, Random random, Logger logger, int minHeight, int maxHeight) {
        this.maxTotal = maxTotal;
        this.random = random;
        this.logger = logger;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    public JumpAndRun generate(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        BoxCollisionDetector shape = new BoxCollisionDetector();

        // start room
        OrientedPart start = createStart(parts, spawnPos);
        shape.add(start.getBounds());

        // start bridge
        Connector startConnector = start.getOut();
        JumpPart endBridge = makeBridge(startConnector);
        shape.add(endBridge.bounds());

        // subsequent rooms and their bridges
        List<JumpPart> rooms = createRooms(parts, startConnector, shape);

        // combine everything
        List<JumpPart> combined = new ArrayList<>();
        combined.add(start.asJumpPart());
        combined.add(endBridge);
        combined.addAll(rooms);

        return new JumpAndRun(combined);
    }

    @NotNull
    private List<JumpPart> createRooms(JumpAndRunSetup.Parts parts, Connector startConnector, BoxCollisionDetector shape) {
        final int maxTries = 10;

        for (int i = 0; i < maxTries; i++) {
            shape.push();

            var rooms = tryGenerateRooms(parts.rooms(), startConnector, shape, parts.end());

            if (rooms != null) {
                return rooms;
            }

            shape.pop();
        }

        logger.warn("Failed to generate jump and run rooms. Fallback to only straight rooms...");

        shape.push();

        var straightRooms = parts.rooms().stream().filter(JumpRoom::isStraight).collect(Collectors.toSet());
        var rooms = tryGenerateRooms(straightRooms, startConnector, shape, parts.end());

        if (rooms != null) {
            return rooms;
        }

        shape.pop();

        logger.error("Failed to generate jump and run with straight rooms. Placing the end room directly...");

        // add the end room in case the room generation failed
        OrientedPart end = createEnd(parts.end(), startConnector);

        shape.add(end.getBounds());

        return List.of(end.asJumpPart());
    }

    @Nullable
    private List<JumpPart> tryGenerateRooms(Set<JumpRoom> rooms, Connector startConnector, BoxCollisionDetector shape, JumpEnd endRoom) {
        Set<JumpRoom> open = new HashSet<>(rooms);

        double avgTime = open.stream().mapToDouble(JumpRoom::getValue).average().orElse(1.0);
        int expected = (int) Math.ceil(maxTotal / avgTime);
        List<JumpPart> parts = new ArrayList<>(expected);

        float currentValue = 0f;
        Connector lastConnector = startConnector;

        // append rooms until the configured cumulative value is reached or there are no rooms left
        while (currentValue < maxTotal && !open.isEmpty()) {
            List<PossibleRoom> possible = possibleRooms(open, lastConnector, shape, endRoom);
            if (possible.isEmpty()) return null;  // maybe use backtracking?

            PossibleRoom possibleRoom = possible.get(random.nextInt(possible.size()));
            if (!shape.add(possibleRoom.part.getBounds())) return null;  // sanity check; should never occur

            parts.add(possibleRoom.part.asJumpPart());
            open.remove(possibleRoom.room);
            lastConnector = possibleRoom.part.getOut();
            currentValue += possibleRoom.room.getValue();

            JumpPart bridge = makeBridge(lastConnector);
            parts.add(bridge);

            if (!shape.add(bridge.bounds())) return null;  // sanity check; should never occur
        }

        OrientedPart end = createEnd(endRoom, lastConnector);
        parts.add(end.asJumpPart());

        if (!shape.add(end.getBounds())) return null;  // sanity check; should never occur

        return parts;
    }

    @NotNull
    private List<PossibleRoom> possibleRooms(Set<JumpRoom> rooms, Connector connector, BoxCollisionDetector shape, JumpEnd endRoom) {
        List<PossibleRoom> possible = new ArrayList<>();

        for (JumpRoom room : rooms) {
            // check if the room itself can be put at the last connector
            OrientedPart part = createPart(room, connector);
            if (isBoxInvalid(part.getBounds(), shape)) continue;

            // check if the bridge to the next room can fit
            Connector out = part.getOut();
            if (isBoxInvalid(getBridgeBounds(out), shape)) continue;

            // check if the end room would fit after the bridge
            OrientedPart nextPart = createEnd(endRoom, out);
            if (isBoxInvalid(nextPart.getBounds(), shape)) continue;

            // room can fit
            possible.add(new PossibleRoom(room, part));
        }

        return possible;
    }

    private boolean isBoxInvalid(BlockBox box, BoxCollisionDetector shape) {
        return box.getMin().getY() < minHeight || box.getMax().getY() > maxHeight || shape.hasCollisions(box);
    }

    private static OrientedPart createPart(JumpRoom room, Connector connector) {
        Direction direction = connector.direction();
        BlockPos pos = connector.pos();

        JumpRoom.Connectors connectors = room.getConnectors();
        Connector entrance = connectors.entrance();

        int targetRotation = direction.getOpposite().getHorizontal();
        int rotation = entrance.direction().getHorizontal() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos entrancePos = entrance.pos();
        Vec3i rotatedEntrance = rotationMatrix.transform(entrancePos);

        Vec3i bridgeOffset = direction.getVector().multiply(2);
        BlockPos entranceOffset = pos.subtract(rotatedEntrance).add(bridgeOffset);

        return new OrientedPart(room.getStructure(), entranceOffset, rotation, entrance, connectors.exit());
    }

    @NotNull
    private OrientedPart createStart(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        JumpEnd startRoom = parts.start();
        BlockStructure startStruct = startRoom.getStructure();

        int rotation = startRoom.getExit().direction().getHorizontal();
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos spawn = Objects.requireNonNull(startRoom.getSpawn(), "Spawn position must be non-null");
        Vec3i rotatedSpawn = rotationMatrix.transform(spawn);
        BlockPos startOffset = spawnPos.subtract(rotatedSpawn);

        return new OrientedPart(startStruct, startOffset, rotation, null, startRoom.getExit());
    }

    @NotNull
    private OrientedPart createEnd(JumpEnd endRoom, Connector connector) {
        BlockStructure endStruct = endRoom.getStructure();

        Connector exit = endRoom.getExit();

        int targetRotation = connector.direction().getOpposite().getHorizontal();
        int rotation = exit.direction().getHorizontal() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        BlockPos exitPos = exit.pos();
        Vec3i rotatedExit = rotationMatrix.transform(exitPos);

        Vec3i bridgeOffset = connector.direction().getVector().multiply(2);
        BlockPos endOffset = connector.pos().subtract(rotatedExit).add(bridgeOffset);

        return new OrientedPart(endStruct, endOffset, rotation, null, exit);
    }

    @NotNull
    private JumpPart makeBridge(Connector connector) {
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
        StructurePrintable printable = new StructurePrintable(structure);
        BlockBox bounds = getBridgeBounds(connector);

        return new JumpPart(printable, bounds);
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

    private record PossibleRoom(JumpRoom room, OrientedPart part) {}
}
