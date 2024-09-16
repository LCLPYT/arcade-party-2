package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import static net.minecraft.util.math.BlockPos.asLong;

public class BoxFloodFill {

    private final Queue<BlockPos> queue = new LinkedList<>();
    private final LongSet visited = new LongOpenHashSet();
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public BoxFloodFill(int width, int height, int length) {
        this(0, 0, 0, width, height, length);
    }

    public BoxFloodFill(int minX, int minY, int minZ, int width, int height, int length) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = minX + width;
        this.maxY = minY + height;
        this.maxZ = minZ + length;
    }

    @SuppressWarnings("DuplicatedCode")
    public synchronized void execute(BlockPos start, Consumer<BlockPos> action, Int3Predicate predicate) {
        queue.clear();
        visited.clear();

        queue.offer(start);
        visited.add(asLong(start.getX(), start.getY(), start.getZ()));

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            action.accept(pos);

            final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // test each neighbor pos "a" and maybe offer to the queue
            int ax, ay, az;

            ax = x + 1;
            ay = y;
            az = z;
            if (ax < maxX && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ax = x - 1;
            if (ax >= minX && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ax = x;
            ay = y + 1;
            if (ay < maxY && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ay = y - 1;
            if (ay >= minY && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ay = y;
            az = z + 1;
            if (az < maxZ && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            az = z - 1;
            if (az >= minZ && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));
        }
    }
}
