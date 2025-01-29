package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

public class StageReader {

    @NotNull
    public static BlockShape readStage(GameMap map) {
        JSONObject area = map.requireProperty("area");
        return readStage(area);
    }

    public static BlockShape readStage(JSONObject json) {
        return readStage(json, null);
    }

    @NotNull
    public static BlockShape readStage(JSONObject json, @Nullable BlockPos spawn) {
        String type = json.getString("type").toLowerCase(Locale.ROOT);

        switch (type) {
            case CylinderBlockShape.TYPE -> {
                BlockPos origin = origin(json, spawn);
                int radius = json.getInt("radius");
                int height = json.getInt("height");

                return new CylinderBlockShape(origin, radius, height);
            }
            case CylinderBlockShape.TYPE_CIRCLE -> {
                BlockPos origin = origin(json, spawn);
                int radius = json.getInt("radius");

                return new CylinderBlockShape(origin, radius, 1);
            }
        }

        throw new IllegalStateException("Unknown area type " + type);
    }

    private static BlockPos origin(JSONObject json, @Nullable BlockPos spawn) {
        return MapUtil.optBlockPos(json.getJSONArray("origin"))
                .or(() -> Optional.ofNullable(spawn))
                .orElseThrow(() -> new NoSuchElementException("Origin undefined"));
    }
}
