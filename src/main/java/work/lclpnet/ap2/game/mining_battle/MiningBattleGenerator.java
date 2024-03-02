package work.lclpnet.ap2.game.mining_battle;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Set;

public class MiningBattleGenerator {

    private final MiningBattleOre ore;
    private final BlockBox box;
    private final Set<BlockState> material;

    public MiningBattleGenerator(MiningBattleOre ore, BlockBox box, Set<BlockState> material) {
        this.ore = ore;
        this.box = box;
        this.material = material;
    }

    public void generateOre(ServerWorld world) {
        var rel = new BlockPos.Mutable();

        LongList positions = scanWorld(world, rel);

        placeOres(world, positions, rel);
    }

    private void placeOres(ServerWorld world, LongList positions, BlockPos.Mutable rel) {
        for (long packed : positions) {
            BlockState newState = ore.getRandomState();

            if (newState == null) continue;

            rel.set(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed));

            world.setBlockState(rel, newState, Block.FORCE_STATE | Block.SKIP_DROPS);
        }
    }

    @NotNull
    private LongList scanWorld(ServerWorld world, BlockPos.Mutable rel) {
        LongList positions = new LongArrayList();

        for (BlockPos pos : box) {
            BlockState state = world.getBlockState(pos);

            if (!material.contains(state)
                || isExposed(world, pos, rel)) continue;

            positions.add(pos.asLong());
        }
        return positions;
    }

    private boolean isExposed(ServerWorld world, BlockPos pos, BlockPos.Mutable rel) {
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        for (Direction dir : Direction.values()) {
            rel.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());

            BlockState state = world.getBlockState(rel);

            if (!state.isOpaqueFullCube(world, pos)) return true;
        }

        return false;
    }
}
