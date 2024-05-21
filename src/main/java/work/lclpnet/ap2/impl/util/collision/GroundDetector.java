package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

public class GroundDetector {

    private final ServerWorld world;
    private final double margin;

    public GroundDetector(ServerWorld world, double margin) {
        this.world = world;
        this.margin = margin;
    }

    public void collectBlocksBelow(ServerPlayerEntity player, Collection<BlockPos> list) {
        double x = player.getX(), y = player.getY() - 1, z = player.getZ();
        var pos = new BlockPos.Mutable();

        for (int dx = -1; dx < 2; dx++) {
            for (int dz = -1; dz < 2; dz++) {
                pos.set(x + margin * dx, y, z + margin * dz);

                if (world.isAir(pos)) continue;

                list.add(pos.toImmutable());
            }
        }
    }
}
