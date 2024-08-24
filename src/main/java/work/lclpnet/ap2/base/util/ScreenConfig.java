package work.lclpnet.ap2.base.util;

import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;

public record ScreenConfig(Vector3fc pos, Vector3fc normal, double width, double height) {

    public static ScreenConfig fromJson(JSONObject json) {
        Vector3f position = MapUtil.readVector3f(json.getJSONArray("position"));

        float yaw = MapUtil.readAngle(json.getNumber("yaw"));

        Vector3f normal = new Vector3f(
                -MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI),
                0.0f,
                -MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI));

        float width = json.getNumber("width").floatValue();
        float height = json.getNumber("height").floatValue();

        return new ScreenConfig(position, normal, width, height);
    }
}
