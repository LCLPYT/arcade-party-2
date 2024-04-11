package work.lclpnet.ap2.impl.util.checkpoint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

public record Checkpoint(BlockPos pos, float yaw, BlockBox bounds) {

    public static Checkpoint fromJson(JSONObject json) {
        BlockPos pos = MapUtil.readBlockPos(json.getJSONArray("pos"));
        float yaw = json.has("yaw") ? MapUtil.readAngle(json.getNumber("yaw")) : 0f;
        BlockBox box = MapUtil.readBox(json.getJSONArray("bounds"));

        return new Checkpoint(pos, yaw, box);
    }

    public Checkpoint relativize(Vec3i origin) {
        BlockBox relativeBounds = new BlockBox(bounds.getMin().subtract(origin), bounds.getMax().subtract(origin));
        return new Checkpoint(pos.subtract(origin), yaw, relativeBounds);
    }

    public Checkpoint transform(AffineIntMatrix mat4) {
        double rad = Math.toRadians(yaw);
        Vec3d vec = mat4.transformVector(Math.sin(-rad), 0d, Math.cos(rad));
        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        BlockBox rotatedBounds = bounds.transform(mat4);

        return new Checkpoint(mat4.transform(pos), yaw, rotatedBounds);
    }
}
