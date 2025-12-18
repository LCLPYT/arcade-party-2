package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockEntity;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.ArrayList;
import java.util.List;

public class MSScanner {

    public static final String
            FUNCTION_POOL = "ap2:maze_scape/function",
            FUNCTION_SPAWN = "ap2:spawn",
            FUNCTION_PIT = "ap2:pit",
            FUNCTION_EXTRA_WALL = "ap2:extra_wall";

    private final Logger logger;
    private final FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

    public MSScanner(Logger logger) {
        this.logger = logger;
    }

    public Result scan(BlockStructure struct) {
        var origin = struct.getOrigin();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        var localPos = new BlockPos.MutableBlockPos();
        var scan = new Scan();

        for (var pos : struct.getBlockPositions()) {
            KibuBlockState kibuState = struct.getBlockState(pos);
            String str = kibuState.getAsString();

            if (!str.startsWith("minecraft:jigsaw")) continue;

            // found a jigsaw
            BlockState state = adapter.revert(kibuState);

            if (state == null) {
                logger.error("Unknown block state {}", str);
                continue;
            }

            KibuBlockEntity kibuBlockEntity = struct.getBlockEntity(pos);

            if (kibuBlockEntity == null) continue;

            CompoundTag nbt = kibuBlockEntity.createNbt();

            localPos.set(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);

            visitJigsaw(localPos, state, nbt, scan);
        }

        return scan;
    }

    private void visitJigsaw(BlockPos.MutableBlockPos localPos, BlockState state, CompoundTag nbt, Scan scan) {
        String name = nbt.getString("name");

        // make sure the jigsaw block name is not empty
        if (name.isEmpty() || name.equals("minecraft:empty")) return;

        BlockPos pos = localPos.immutable();
        scan.jigsaws.add(pos);

        String pool = nbt.getString("pool");

        if (FUNCTION_POOL.equals(pool)) {
            // found a function jigsaw
            switch (name) {
                case FUNCTION_SPAWN -> scan.spawn = new Vec3(localPos.getX() + 0.5, localPos.getY() + 1, localPos.getZ() + 0.5);
                case FUNCTION_PIT -> scan.pitMarkers.add(localPos.immutable());
                case FUNCTION_EXTRA_WALL -> scan.wallMarkers.add(new WallMarker(pos, state.getValue(BlockStateProperties.ORIENTATION)));
                default -> {}
            }
            return;
        }

        String target = nbt.getString("target");

        if (!target.isEmpty() && !target.equals("minecraft:empty")) {
            // found a connector
            FrontAndTop orientation = state.getValue(BlockStateProperties.ORIENTATION);

            scan.connectors.add(new Connector3(pos, orientation, name, target));
            scan.wallMarkers.add(new WallMarker(pos, orientation));
        }
    }

    public interface Result {
        List<Connector3> connectors();
        List<WallMarker> wallMarkers();
        List<BlockPos> jigsaws();
        @Nullable Vec3 spawn();
        List<BlockPos> pitMarkers();
    }

    private static class Scan implements Result {
        final List<Connector3> connectors = new ArrayList<>(2);
        final List<WallMarker> wallMarkers = new ArrayList<>(2);
        final List<BlockPos> jigsaws = new ArrayList<>(2);
        final List<BlockPos> pitMarkers = new ArrayList<>(0);
        @Nullable Vec3 spawn = null;

        public List<Connector3> connectors() {
            return connectors;
        }

        @Override
        public List<WallMarker> wallMarkers() {
            return wallMarkers;
        }

        @Override
        public List<BlockPos> jigsaws() {
            return jigsaws;
        }

        public @Nullable Vec3 spawn() {
            return spawn;
        }

        @Override
        public List<BlockPos> pitMarkers() {
            return pitMarkers;
        }
    }
}
