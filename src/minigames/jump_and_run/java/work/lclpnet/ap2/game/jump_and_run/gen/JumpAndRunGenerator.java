package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.WeightedList;
import work.lclpnet.gaco.math.BlockFace;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static net.minecraft.block.HorizontalFacingBlock.FACING;

public class JumpAndRunGenerator {

    private final float targetMinutes;
    private final Random random;

    public JumpAndRunGenerator(float targetMinutes, Random random) {
        this.targetMinutes = targetMinutes;
        this.random = random;
    }

    public List<JumpModule> generate(JumpAndRunSetup.Parts parts) {
        WeightedList<JumpModule> rooms = new WeightedList<>();

        for (var module : parts.modules()) {
            rooms.add(module, module.data().weight());
        }

        List<JumpModule> modules = new ArrayList<>();
        float minutes = 0;

        while (minutes < targetMinutes && !rooms.isEmpty()) {
            int i = rooms.getRandomIndex(random);  // TODO seamless queue
            JumpModule room = rooms.remove(i);

            modules.add(room);
            minutes += room.data().estimatedMinutes();
        }

        return modules;
    }

    public record JumpStructure(List<JumpPart> parts, Checkpoint checkpoint) {

        public void place(ServerWorld world) {
            for (JumpPart part : parts) {
                StructureUtil.placeStructureFast(part, world);
            }
        }
    }

    public JumpStructure getEntrance(JumpAndRunSetup.Parts parts, BlockFace connector) {
        OrientedPart part = createEndPart(parts.start(), connector);

        Connector partConnector = requireNonNull(part.out());
        Bridge bridge = makeBridge(partConnector);

        BlockPos spawn = requireNonNull(part.spawn(), "Spawn position must be non-null");
        float yaw = MathUtil.yaw(connector.face().getOpposite().getDoubleVector());

        var checkpoint = new Checkpoint(spawn.toBottomCenterPos(), yaw, 0f, bridge.bounds());

        return new JumpStructure(List.of(part, bridge), checkpoint);
    }

    public JumpStructure getExit(JumpAndRunSetup.Parts parts, BlockFace connector) {
        OrientedPart part = createEndPart(parts.end(), connector);

        Connector partConnector = requireNonNull(part.out());
        Bridge bridge = makeBridge(partConnector);

        float yaw = MathUtil.yaw(connector.face().getDoubleVector());

        var checkpoint = new Checkpoint(bridge.spawn().toBottomCenterPos(), yaw, 0f, part.bounds());

        return new JumpStructure(List.of(part, bridge), checkpoint);
    }

    @NotNull
    private OrientedPart createEndPart(JumpEnd startRoom, BlockFace connector) {
        BlockStructure startStruct = startRoom.structure();

        Connector exit = startRoom.exit();

        int targetRotation = connector.face().getOpposite().getHorizontalQuarterTurns();
        int rotation = exit.direction().getHorizontalQuarterTurns() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        Vec3i rotatedExitPos = rotationMatrix.transform(exit.pos());

        BlockPos offset = connector.pos().subtract(rotatedExitPos).offset(connector.face(), 2);

        BlockPos spawn = startRoom.spawn();
        BlockPos transformedSpawn = spawn != null ? rotationMatrix.transform(spawn).add(offset) : null;

        return OrientedPart.createTransformed(startStruct, offset, rotation, null, exit, transformedSpawn);
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
}
