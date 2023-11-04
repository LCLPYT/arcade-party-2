package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import work.lclpnet.ap2.impl.util.Vec2i;

public class MapUtil {

    /**
     * Reads two block positions from a {@link JSONArray}.
     * The first element of the resulting tuple will be this minimum position and the second the maximum.
     * @param tuple The json input.
     * @return A {@link Pair} of {@link BlockPos}. The first element is the minimum, the second the maximum.
     */
    public static Pair<BlockPos, BlockPos> readBox(JSONArray tuple) {
        if (tuple.length() < 2) throw new IllegalArgumentException("Tuple must be of size 2");

        JSONArray first = tuple.getJSONArray(0);
        JSONArray second = tuple.getJSONArray(1);

        if (first.length() < 3) throw new IllegalArgumentException("First tuple element must be of size 3");
        if (second.length() < 3) throw new IllegalArgumentException("Second tuple element must be of size 3");

        int x1 = first.getInt(0), y1 = first.getInt(1), z1 = first.getInt(2);
        int x2 = second.getInt(0), y2 = second.getInt(1), z2 = second.getInt(2);

        return Pair.of(
                new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)),
                new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2))
        );
    }

    public static BlockPos readBlockPos(JSONArray tuple) {
        if (tuple.length() < 3) throw new IllegalArgumentException("Tuple must be of size 3");

        return new BlockPos(tuple.getInt(0), tuple.getInt(1), tuple.getInt(2));
    }

    public static Vec2i readVec2i(JSONArray tuple) {
        if (tuple.length() < 2) throw new IllegalArgumentException("Tuple must be of size 2");

        return new Vec2i(tuple.getInt(0), tuple.getInt(1));
    }

    private MapUtil() {}
}
