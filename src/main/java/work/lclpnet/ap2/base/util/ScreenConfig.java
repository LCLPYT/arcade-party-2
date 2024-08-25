package work.lclpnet.ap2.base.util;

import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.math.MathUtil;

public record ScreenConfig(Vector3fc pos, Vector3fc normal, float width, float height) {

    public ScreenConfig {
        normal = new Vector3f(normal).normalize();
    }

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

    public Vector3f center() {
        var right = right();
        var up = normal.cross(right, new Vector3f());

        return up.mul(height * 0.5f)
                .add(right.mul(width * 0.5f))
                .add(pos);
    }

    public Vector3f up() {
        Vector3f right = right();
        return normal.cross(right, right);
    }

    public Vector3f right() {
        return normal.rotateY((float) (Math.PI * 0.5), new Vector3f());
    }

    public Quaternionf textRotation() {
        return MathUtil.rotation(new Vector3f(0, 0, 1), normal);
    }
}
