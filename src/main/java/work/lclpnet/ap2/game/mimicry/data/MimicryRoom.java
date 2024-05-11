package work.lclpnet.ap2.game.mimicry.data;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record MimicryRoom(BlockPos pos, BlockPos spawn, float yaw) {

    public void teleport(ServerPlayerEntity player, ServerWorld world) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleport(world, x, y, z, yaw, 0.0F);
    }
}
