package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONException;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;

public record SbIslandData(String id, BlockPos spawn, float yaw, BlockBox buildArea, Vec3i previewOffset) {

    public static SbIslandData fromJson(JSONObject json) throws JSONException {
        String id = json.getString("id");
        BlockPos spawn = MapUtil.readBlockPos(json.getJSONArray("spawn"));
        float yaw;

        if (json.has("yaw")) {
            yaw = MapUtil.readAngle(json.getNumber("yaw"));
        } else {
            yaw = 0;
        }

        BlockBox buildArea = MapUtil.readBox(json.getJSONArray("build-area"));
        Vec3i previewOffset = MapUtil.readBlockPos(json.getJSONArray("preview-offset"));

        return new SbIslandData(id, spawn, yaw, buildArea, previewOffset);
    }
}
