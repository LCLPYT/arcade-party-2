package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Locale;

public class StageReader {

    private final GameMap map;

    public StageReader(GameMap map) {
        this.map = map;
    }

    public Stage readStage() {
        JSONObject area = map.requireProperty("area");
        String type = area.getString("type").toLowerCase(Locale.ROOT);

        switch (type) {
            case CylinderStage.TYPE -> {
                BlockPos origin = MapUtil.readBlockPos(area.getJSONArray("origin"));
                int radius = area.getInt("radius");
                int height = area.getInt("height");
                return new CylinderStage(origin, radius, height);
            }
            case CylinderStage.TYPE_CIRCLE -> {
                BlockPos origin = MapUtil.readBlockPos(area.getJSONArray("origin"));
                int radius = area.getInt("radius");
                return new CylinderStage(origin, radius, 1);
            }
        }

        throw new IllegalStateException("Unknown area type " + type);
    }
}
