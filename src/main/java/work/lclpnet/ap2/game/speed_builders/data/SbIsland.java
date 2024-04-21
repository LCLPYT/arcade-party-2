package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos spawnWorldPos;
    private final BlockBox buildingArea;

    /**
     * Constructor.
     * @param data The island data.
     * @param origin The origin of the schematic, used to calculate relative data.
     * @param offset The offset the island structure is placed at in world coordinates.
     */
    public SbIsland(SbIslandData data, BlockPos origin, BlockPos offset) {
        this.data = data;

        BlockPos relSpawn = data.spawn().subtract(origin);
        this.spawnWorldPos = offset.add(relSpawn);

        AffineIntMatrix mat4 = AffineIntMatrix.makeTranslation(
                offset.getX() - origin.getX(),
                offset.getY() - origin.getY(),
                offset.getZ() - origin.getZ());

        this.buildingArea = data.buildArea().transform(mat4);
    }

    public void teleport(ServerPlayerEntity player) {
        double x = spawnWorldPos.getX() + 0.5, y = spawnWorldPos.getY(), z = spawnWorldPos.getZ() + 0.5;
        ServerWorld world = player.getServerWorld();

        player.teleport(world, x, y, z, data.yaw(), 0);
    }

    public boolean isWithinBuildingArea(BlockPos pos) {
        return buildingArea.contains(pos);
    }
}
