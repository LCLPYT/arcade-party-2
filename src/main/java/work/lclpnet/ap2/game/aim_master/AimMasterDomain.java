package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class AimMasterDomain {

    //private final ServerPlayNetworkHandler networkHandler;
    private final BlockPos spawn;
    private final ServerWorld world;
    private final float yaw;

    public AimMasterDomain(BlockPos spawn, float yaw, ServerWorld world) {
        this.spawn = spawn;
        this.yaw = yaw;
        this.world = world;
    }

    public void teleport(ServerPlayerEntity player) {
        player.teleport(world, spawn.getX()+0.5, spawn.getY(), spawn.getZ()+0.5, yaw, 0);
    }

    public void setBlocks(AimMasterSequence.Item item) {
        Map<BlockPos, Block> blockMap = item.blockMap();
        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.add(spawn);
            world.setBlockState(offsetPos, blockMap.get(pos).getDefaultState());
        }
    }

    public void removeBlocks(AimMasterSequence.Item item) {
        Map<BlockPos, Block> blockMap = item.blockMap();
        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.add(spawn);
            world.setBlockState(offsetPos, Blocks.AIR.getDefaultState());
        }
    }
}
