package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos origin;
    private final BlockPos offset;

    public SbIsland(SbIslandData data, BlockPos origin, BlockPos offset) {
        this.data = data;
        this.origin = origin;
        this.offset = offset;
    }

    public void teleport(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        BlockPos relSpawn = data.spawn().subtract(origin);
        Vec3d spawn = Vec3d.ofBottomCenter(offset.add(relSpawn));

        player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), data.yaw(), 0);
    }
}
