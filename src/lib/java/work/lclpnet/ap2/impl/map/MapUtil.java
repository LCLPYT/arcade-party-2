package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.ap2.impl.util.world.block_shape.BoxBlockShape;
import work.lclpnet.ap2.impl.util.world.block_shape.CylinderBlockShape;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.gaco.math.Vec2i;
import work.lclpnet.kibu.util.BlockStateUtils;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;

import static java.lang.Math.floor;

public class MapUtil {

    /**
     * Reads two block positions from a {@link JSONArray}.
     * The first element of the resulting tuple will be this minimum position and the second the maximum.
     * @param tuple The json input.
     * @return A {@link Pair} of {@link BlockPos}. The first element is the minimum, the second the maximum.
     */
    public static BlockBox readBox(JSONArray tuple) {
        if (tuple.length() < 2) throw new IllegalArgumentException("Tuple must be of size 2");

        JSONArray first = tuple.getJSONArray(0);
        JSONArray second = tuple.getJSONArray(1);

        if (first.length() < 3) throw new IllegalArgumentException("First tuple element must be of size 3");
        if (second.length() < 3) throw new IllegalArgumentException("Second tuple element must be of size 3");

        int x1 = first.getInt(0), y1 = first.getInt(1), z1 = first.getInt(2);
        int x2 = second.getInt(0), y2 = second.getInt(1), z2 = second.getInt(2);

        return new BlockBox(x1, y1, z1, x2, y2, z2);
    }

    public static BlockPos readBlockPos(JSONArray tuple) {
        return optBlockPos(tuple).orElseThrow(() -> new IllegalArgumentException("Tuple must be of size 3"));
    }

    public static Optional<BlockPos> optBlockPos(JSONArray tuple) {
        if (tuple.length() < 3) return Optional.empty();

        return Optional.of(new BlockPos(tuple.getInt(0), tuple.getInt(1), tuple.getInt(2)));
    }

    public static Vec3d readVec3d(JSONArray tuple) {
        return optVec3d(tuple).orElseThrow(() -> new IllegalArgumentException("Tuple must be of size 3"));
    }

    public static Optional<Vec3d> optVec3d(JSONArray tuple) {
        if (tuple.length() < 3) return Optional.empty();

        return Optional.of(new Vec3d(tuple.getDouble(0), tuple.getDouble(1), tuple.getDouble(2)));
    }

    public static Vec3d readCenteredVec3d(JSONArray tuple) {
        return optCenteredVec3d(tuple).orElseThrow(() -> new IllegalArgumentException("Tuple must be of size 3"));
    }

    public static Optional<Vec3d> optCenteredVec3d(JSONArray tuple) {
        if (tuple.length() < 3) return Optional.empty();

        return Optional.of(new Vec3d(
                centeredDouble(tuple.getDouble(0)),
                centeredDouble(tuple.getDouble(1)),
                centeredDouble(tuple.getDouble(2))
        ));
    }

    public static Vec2i readVec2i(JSONArray tuple) {
        if (tuple.length() < 2) throw new IllegalArgumentException("Tuple must be of size 2");

        return new Vec2i(tuple.getInt(0), tuple.getInt(1));
    }

    public static int readInt(Number number) {
        return number.intValue();
    }

    public static float readFloat(Number number) {
        return number.floatValue();
    }

    public static double readDouble(Number number) {
        return number.doubleValue();
    }

    /**
     * Read an angle in degrees.
     * @param number A number.
     * @return An angle in degrees between [-180, 180).
     */
    public static float readAngle(Number number) {
        return MathHelper.wrapDegrees(readFloat(number));
    }

    public static BlockState readBlockState(String string) {
        return Objects.requireNonNull(BlockStateUtils.parse(string), "Unknown block state");
    }

    public static void readBlockStates(JSONArray array, Collection<BlockState> states, Logger logger) {
        for (Object obj : array) {
            if (!(obj instanceof String str)) {
                logger.warn("Invalid block state array entry {}", obj);
                continue;
            }

            BlockState state = readBlockState(str);
            states.add(state);
        }
    }

    @NotNull
    public static BlockShape readArea(GameMap map) {
        JSONObject area = map.requireProperty("area");
        return readShape(area);
    }

    public static @Nullable BlockShape readOptShape(GameMap map, String key) {
        JSONObject json = map.getProperties().optJSONObject(key);

        if (json == null) return null;

        BlockPos mapSpawn = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));

        return readShape(json, mapSpawn);
    }

    public static BlockShape readShape(GameMap map, String key) {
        BlockPos mapSpawn = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));

        return readShape(map.requireProperty(key), mapSpawn);
    }

    public static BlockShape readShape(JSONObject json) {
        return readShape(json, null);
    }

    @NotNull
    public static BlockShape readShape(JSONObject json, @Nullable BlockPos spawn) {
        String type = json.getString("type").toLowerCase(Locale.ROOT);

        return switch (type) {
            case CylinderBlockShape.TYPE -> {
                BlockPos origin = origin(json, spawn);
                int radius = json.getInt("radius");
                int height = json.getInt("height");

                yield new CylinderBlockShape(origin, radius, height);
            }
            case CylinderBlockShape.TYPE_CIRCLE -> {
                BlockPos origin = origin(json, spawn);
                int radius = json.getInt("radius");

                yield new CylinderBlockShape(origin, radius, 1);
            }
            case BoxBlockShape.TYPE_CUBE -> {
                BlockPos origin = origin(json, spawn);
                int radius = json.getInt("radius");

                yield new BoxBlockShape(BlockBox.ofRadius(origin, radius));
            }
            case BoxBlockShape.TYPE_BOX -> {
                JSONArray tuple = json.getJSONArray("bounds");
                BlockBox box = readBox(tuple);

                yield new BoxBlockShape(box);
            }
            default -> throw new IllegalStateException("Unknown area type " + type);
        };
    }

    private static BlockPos origin(JSONObject json, @Nullable BlockPos fallback) {
        return Optional.ofNullable(json.optJSONArray("origin"))
                .flatMap(MapUtil::optBlockPos)
                .or(() -> Optional.ofNullable(fallback))
                .orElseThrow(() -> new NoSuchElementException("Origin undefined"));
    }

    public static double centeredDouble(double d) {
        int i = (int) floor(d);

        if (Math.abs(d - i) < 1e-6) {
            // return centered block pos
            return i + 0.5;
        }

        return d;
    }

    public static Optional<SplinePath> readSplinePath(JSONArray json, Logger logger) {
        List<Vec3d> keypoints = new ArrayList<>(json.length());

        for (Object item : json) {
            if (!(item instanceof JSONArray tuple)) {
                logger.error("Invalid spline path element: {}", item);
                continue;
            }

            keypoints.add(MapUtil.readCenteredVec3d(tuple));
        }

        return SplinePath.create(keypoints, logger);
    }


    private MapUtil() {}
}
