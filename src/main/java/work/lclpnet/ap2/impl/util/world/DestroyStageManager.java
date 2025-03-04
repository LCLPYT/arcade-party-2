package work.lclpnet.ap2.impl.util.world;

import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import static java.lang.Math.floorMod;

public class DestroyStageManager {

    private static final int
            RESERVED_BITS = 8,
            XZ_BITS = 8,
            Y_BITS = 32 - RESERVED_BITS - 2 * XZ_BITS,
            MAX_XZ = (1 << XZ_BITS) - 1,
            MAX_Y = (1 << Y_BITS) - 1,
            MIN_ID = 1 << RESERVED_BITS;

    private final ServerWorld world;
    private final int rangeSq;

    public DestroyStageManager(ServerWorld world) {
        this(world, 400);
    }

    public DestroyStageManager(ServerWorld world, int range) {
        this.world = world;
        this.rangeSq = range * range;
    }

    /**
     * Set the breaking progress / destroy stage for a block.
     * This is sent to all players in the specified range.
     * @param pos The block position.
     * @param progress The destroy progress in (0, 9).
     *                 Where zero is the first breaking texture being shown and 9 the last.
     *                 To remove the breaking progress, use any other value, e.g. -1.
     */
    public void setDestroyStage(BlockPos pos, int progress) {
        int id = id(pos);
        var packet = new BlockBreakingProgressS2CPacket(id, pos, progress);

        for (ServerPlayerEntity player : world.getPlayers()) {
            int dx = player.getBlockX() - pos.getX();
            int dy = player.getBlockY() - pos.getY();
            int dz = player.getBlockZ() - pos.getZ();

            int distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > rangeSq) continue;

            player.networkHandler.sendPacket(packet);
        }
    }

    /**
     * Remove the breaking progress / destroy stage for a block.
     * This is sent to all players in the specified range.
     * @param pos The block position.
     */
    public void removeDestroyStage(BlockPos pos) {
        setDestroyStage(pos, -1);
    }

    public int id(BlockPos pos) {
        int x = floorMod(pos.getX(), MAX_XZ);
        int z = floorMod(pos.getZ(), MAX_XZ);
        int y = floorMod(pos.getY(), MAX_Y);

        int id = (x << (XZ_BITS * 2)) | (z << (XZ_BITS)) | y;

        return id + MIN_ID;
    }
}
