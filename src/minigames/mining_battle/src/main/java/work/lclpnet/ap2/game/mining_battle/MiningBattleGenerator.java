package work.lclpnet.ap2.game.mining_battle;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.gaco.ds.BlockBox;

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

    public void generateOre(ServerLevel world) {
        var rel = new BlockPos.MutableBlockPos();

        LongList positions = scanWorld(world, rel);

        placeOres(world, positions, rel);
    }

    private void placeOres(ServerLevel world, LongList positions, BlockPos.MutableBlockPos rel) {
        for (long packed : positions) {
            BlockState newState = ore.getRandomState();

            if (newState == null) continue;

            rel.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));

            world.setBlock(rel, newState, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @NotNull
    private LongList scanWorld(ServerLevel world, BlockPos.MutableBlockPos rel) {
        LongList positions = new LongArrayList();

        for (BlockPos pos : box) {
            BlockState state = world.getBlockState(pos);

            if (!material.contains(state)
                || isExposed(world, pos, rel)) continue;

            positions.add(pos.asLong());
        }
        return positions;
    }

    private boolean isExposed(ServerLevel world, BlockPos pos, BlockPos.MutableBlockPos rel) {
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        for (Direction dir : Direction.values()) {
            rel.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());

            BlockState state = world.getBlockState(rel);

            if (!state.isSolidRender()) return true;
        }

        return false;
    }
}
