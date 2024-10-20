package work.lclpnet.ap2.impl.util.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.util.math.ChunkPos.*;

public class ChunkPersistence {

    private final ServerWorld world;
    private final LongSet chunks = new LongOpenHashSet();

    public ChunkPersistence(ServerWorld world, MiniGameHandle gameHandle) {
        this.world = world;

        gameHandle.whenDone(this::reset);
    }

    public void markPersistent(int chunkX, int chunkZ) {
        if (!chunks.add(toLong(chunkX, chunkZ))) return;

        setForced(chunkX, chunkZ, true);
    }

    public void removePersistent(int chunkX, int chunkZ) {
        if (!chunks.remove(toLong(chunkX, chunkZ))) return;

        setForced(chunkX, chunkZ, false);
    }

    private void setForced(int chunkX, int chunkZ, boolean forced) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        world.getChunkManager().setChunkForced(pos, forced);
    }

    /**
     * Marks all chunks within a given quad as persistent.
     * The order of the minimum and maximum coordinate doesn't matter, as this method uses the min() and max() of each coordinate pair.
     *
     * @param fromChunkX Begin chunkX of the quad, inclusive.
     * @param fromChunkZ Begin chunkZ of the quad, inclusive.
     * @param toChunkX End chunkX of the quad, exclusive.
     * @param toChunkZ End chunkZ of the quad, exclusive.
     */
    public void markQuadPersistent(int fromChunkX, int fromChunkZ, int toChunkX, int toChunkZ) {
        int minX = min(fromChunkX, toChunkX);
        int maxX = max(fromChunkX, toChunkX);
        int minZ = min(fromChunkZ, toChunkZ);
        int maxZ = max(fromChunkZ, toChunkZ);

        for (int cx = minX; cx < maxX; cx++) {
            for (int cz = minZ; cz < maxZ; cz++) {
                markPersistent(cx, cz);
            }
        }
    }

    public void reset() {
        for (long chunk : chunks) {
            int cx = getPackedX(chunk);
            int cz = getPackedZ(chunk);

            removePersistent(cx, cz);
        }
    }
}
