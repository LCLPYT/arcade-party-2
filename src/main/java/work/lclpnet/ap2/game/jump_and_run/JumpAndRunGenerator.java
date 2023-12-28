package work.lclpnet.ap2.game.jump_and_run;

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
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.StackCollisionDetector;
import work.lclpnet.ap2.impl.util.math.Matrix3i;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;

import java.util.*;

import static net.minecraft.block.HorizontalFacingBlock.FACING;

public class JumpAndRunGenerator {

    private final float maxTotal;
    private final Random random;
    private final Logger logger;

    public JumpAndRunGenerator(float maxTotal, Random random, Logger logger) {
        this.maxTotal = maxTotal;
        this.random = random;
        this.logger = logger;
    }

    public JumpAndRun generate(JumpAndRunSetup.Parts parts, BlockPos spawnPos) {
        StackCollisionDetector shape = new StackCollisionDetector(new ChunkedCollisionDetector());

        OrientedPart start = createStart(parts, spawnPos);
        shape.add(start.getBounds());

        List<JumpPart> rooms = createRooms(parts, start.getOut(), shape);

        List<JumpPart> combined = new ArrayList<>();
        combined.add(start.asJumpPart());
        combined.addAll(rooms);

        return new JumpAndRun(combined);
    }

    @NotNull
    private List<JumpPart> createRooms(JumpAndRunSetup.Parts parts, Connector startConnector, StackCollisionDetector shape) {
        final int maxTries = 10;

        for (int i = 0; i < maxTries; i++) {
            var selection = getRandomRooms(parts.rooms());

            var rooms = createRoomsFromSelection(selection, shape);

            if (rooms != null) {
                return rooms.stream().map(OrientedPart::asJumpPart).toList();
            }
        }

        logger.error("Failed to generate jump and run rooms");

        // add the end room in case the room generation failed
        JumpPart endBridge = makeBridge(startConnector);
        OrientedPart end = createEnd(parts, startConnector);

        return List.of(endBridge, end.asJumpPart());
    }

    @Nullable
    private List<OrientedPart> createRoomsFromSelection(List<JumpRoom> rooms, StackCollisionDetector shape) {
        rooms.sort(Comparator.comparing(JumpRoom::getValue));

        var built = buildRooms(rooms, shape);

        if (built != null) return built;

        final int maxTries = 10;

        for (int i = 0; i < maxTries; i++) {
            Collections.shuffle(rooms);

            built = buildRooms(rooms, shape);

            if (built != null) return built;
        }

        return null;
    }

    @Nullable
    private List<OrientedPart> buildRooms(List<JumpRoom> rooms, StackCollisionDetector shape) {
        shape.push();

        // TODO try to join rooms at their connectors and return parts if successful. also consider the end room

        shape.pop();
        return null;
    }

    private List<JumpRoom> getRandomRooms(Set<JumpRoom> parts) {
        List<JumpRoom> open = new ArrayList<>(parts);
        List<JumpRoom> selection = new ArrayList<>(4);  // estimated 1.2 min per room

        float total = 0f;

        while (!open.isEmpty() && total < maxTotal) {
            int index = random.nextInt(open.size());
            JumpRoom room = open.remove(index);
            selection.add(room);

            total += room.getValue();
        }

        return selection;
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
    private OrientedPart createEnd(JumpAndRunSetup.Parts parts, Connector connector) {
        JumpEnd endRoom = parts.end();
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
        BlockBox bounds = new BlockBox(bridgePos.add(up2).add(side2), bridgePos.subtract(up2).subtract(side2));

        return new JumpPart(printable, bounds);
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
