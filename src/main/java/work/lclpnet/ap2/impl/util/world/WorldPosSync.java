package work.lclpnet.ap2.impl.util.world;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

public class WorldPosSync {

    private final Vector3d worldPos = new Vector3d(0);
    private Vec3d mcWorldPos = Vec3d.ZERO;

    public Vec3d mcWorldPos() {
        return mcWorldPos;
    }

    public void update(Matrix4dc matrixWorld) {
        matrixWorld.transformPosition(worldPos.zero());

        if (worldPos.x != mcWorldPos.x || worldPos.y != mcWorldPos.y || worldPos.z != mcWorldPos.z) {
            mcWorldPos = new Vec3d(worldPos.x, worldPos.y, worldPos.z);
        }
    }
}
