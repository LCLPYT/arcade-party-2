package work.lclpnet.ap2.game.jump_and_run.gen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.game.GameInfo;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.queue.JsonFileQueuePersistence;
import work.lclpnet.gaco.ds.queue.QueuePersistence;
import work.lclpnet.gaco.ds.queue.SeamlessQueue;
import work.lclpnet.gaco.math.BlockFace;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.structure.SimpleBlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.floor;
import static java.util.Objects.requireNonNull;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class JumpAndRunGenerator {

    private final float targetMinutes;
    private final Random random;
    private final QueuePersistence<String> queuePersistence;
    private @Nullable SeamlessQueue<JumpModule> queue = null;

    public JumpAndRunGenerator(GameInfo gameInfo, float targetMinutes, Random random, Logger logger) {
        this.targetMinutes = targetMinutes;
        this.random = random;

        queuePersistence = JsonFileQueuePersistence.create(ApConstants.RUNTIME_CONFIG_ID, gameInfo.identifier("module_queue"), Codec.STRING, logger);
    }

    public List<JumpModule> generate(JumpAndRunSetup.Parts parts) {
        queue = createQueue(Set.copyOf(parts.modules()));

        List<JumpModule> modules = new ArrayList<>();
        float minutes = 0;

        while (minutes < targetMinutes) {
            JumpModule module = queue.next();

            modules.add(module);
            
            minutes += module.data().estimatedMinutes();
        }

        return modules;
    }

    private SeamlessQueue<JumpModule> createQueue(Set<JumpModule> pool) {
        int margin = (int) floor(pool.size() * 0.4f);

        var byPath = pool.stream().collect(Collectors.toMap(JumpModule::path, Function.identity()));
        var restored = queuePersistence.restore();

        return new SeamlessQueue<>(pool, random, margin, restored.map(byPath::get));
    }

    public record JumpStructure(List<JumpPart> parts, Checkpoint checkpoint) {
        public void place(ServerLevel world) {
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
        float yaw = MathUtil.yaw(connector.face().getOpposite().getUnitVec3());

        var checkpoint = new Checkpoint(spawn.getBottomCenter(), yaw, 0f, bridge.bounds());

        return new JumpStructure(List.of(part, bridge), checkpoint);
    }

    public JumpStructure getExit(JumpAndRunSetup.Parts parts, BlockFace connector) {
        OrientedPart part = createEndPart(parts.end(), connector);

        Connector partConnector = requireNonNull(part.out());
        Bridge bridge = makeBridge(partConnector);

        float yaw = MathUtil.yaw(connector.face().getUnitVec3());

        var checkpoint = new Checkpoint(bridge.spawn().getBottomCenter(), yaw, 0f, part.bounds());

        return new JumpStructure(List.of(part, bridge), checkpoint);
    }

    @NotNull
    private OrientedPart createEndPart(JumpEnd startRoom, BlockFace connector) {
        BlockStructure startStruct = startRoom.structure();

        Connector exit = startRoom.exit();

        int targetRotation = connector.face().getOpposite().get2DDataValue();
        int rotation = exit.direction().get2DDataValue() - targetRotation;
        Matrix3i rotationMatrix = Matrix3i.makeRotationY(rotation);

        Vec3i rotatedExitPos = rotationMatrix.transform(exit.pos());

        BlockPos offset = connector.pos().subtract(rotatedExitPos).relative(connector.face(), 2);

        BlockPos spawn = startRoom.spawn();
        BlockPos transformedSpawn = spawn != null ? rotationMatrix.transform(spawn).offset(offset) : null;

        return OrientedPart.createTransformed(startStruct, offset, rotation, null, exit, transformedSpawn);
    }

    @NotNull
    private Bridge makeBridge(Connector connector) {
        BlockPos pos = connector.pos();
        Direction dir = connector.direction();
        Vec3i vec = dir.getUnitVec3i();
        Vec3i up = Direction.UP.getUnitVec3i();
        Vec3i side = vec.cross(up);  // up and vec are orthogonal and unit, thus right is a unit vector

        BlockState base = Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState();
        BlockState upState = base.setValue(FACING, dir);
        BlockState downState = base.setValue(FACING, dir.getOpposite());
        BlockState sideState = base.setValue(FACING, dir.getCounterClockWise());

        Vec3i up2 = up.multiply(2);
        Vec3i side2 = side.multiply(2);
        BlockPos bridgePos = pos.offset(vec);

        Map<BlockPos, BlockState> blocks = new HashMap<>();

        buildAxis(bridgePos, side, up2, upState, downState, blocks);
        buildAxis(bridgePos, up, side2, sideState, sideState, blocks);

        BlockStructure structure = makeStructure(blocks);
        BlockBox bounds = getBridgeBounds(connector);

        return new Bridge(structure, bounds, pos.below().offset(vec), dir, BlockPos.ZERO);
    }

    @NotNull
    private static BlockBox getBridgeBounds(Connector connector) {
        Vec3i vec = connector.direction().getUnitVec3i();
        Vec3i up = Direction.UP.getUnitVec3i();
        Vec3i side = vec.cross(up);  // up and vec are orthogonal and unit, thus right is a unit vector

        Vec3i up2 = up.multiply(2);
        Vec3i side2 = side.multiply(2);
        BlockPos bridgePos = connector.pos().offset(vec);

        return new BlockBox(bridgePos.offset(up2).offset(side2), bridgePos.subtract(up2).subtract(side2));
    }

    private void buildAxis(BlockPos pos, Vec3i side, Vec3i axis, BlockState upState, BlockState downState, Map<BlockPos, BlockState> blocks) {
        blocks.put(pos.offset(axis), upState);
        blocks.put(pos.offset(axis).offset(side), upState);
        blocks.put(pos.offset(axis).subtract(side), upState);
        blocks.put(pos.subtract(axis), downState);
        blocks.put(pos.subtract(axis).offset(side), downState);
        blocks.put(pos.subtract(axis).subtract(side), downState);
    }

    private BlockStructure makeStructure(Map<BlockPos, BlockState> blocks) {
        SimpleBlockStructure structure = FabricStructureWrapper.createSimpleStructure();
        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        blocks.forEach((pos, state) -> structure.setBlockState(adapter.adapt(pos), adapter.adapt(state)));
        return structure;
    }

    public void pushModuleHistory(JumpModule module) {
        if (queue == null) return;

        queue.pushElement(module);

        CompletableFuture.runAsync(() -> queuePersistence.store(queue.transfer().map(JumpModule::path)));
    }
}
