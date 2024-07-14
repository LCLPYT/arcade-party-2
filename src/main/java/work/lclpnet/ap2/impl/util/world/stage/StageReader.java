package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Locale;

public class StageReader {

    @NotNull
    public static Stage readStage(GameMap map) {
        JSONObject area = map.requireProperty("area");
        return readStage(area);
    }

    @NotNull
    public static Stage readStage(JSONObject json) {
        String type = json.getString("type").toLowerCase(Locale.ROOT);

        switch (type) {
            case CylinderStage.TYPE -> {
                BlockPos origin = MapUtil.readBlockPos(json.getJSONArray("origin"));
                int radius = json.getInt("radius");
                int height = json.getInt("height");
                return new CylinderStage(origin, radius, height);
            }
            case CylinderStage.TYPE_CIRCLE -> {
                BlockPos origin = MapUtil.readBlockPos(json.getJSONArray("origin"));
                int radius = json.getInt("radius");
                return new CylinderStage(origin, radius, 1);
            }
        }

        throw new IllegalStateException("Unknown area type " + type);
    }
}
