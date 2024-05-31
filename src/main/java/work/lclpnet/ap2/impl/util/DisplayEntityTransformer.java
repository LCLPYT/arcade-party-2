package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.*;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class DisplayEntityTransformer {

    private final Vector3d position = new Vector3d();
    private final Vector3d scale = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private final Matrix4f mat4f = new Matrix4f();
    private final Matrix4d prevMatrix = new Matrix4d();

    public void applyTransformation(DisplayEntity display, Matrix4dc matrix) {
        AffineTransformation transformation;

        synchronized (this) {
            if (matrix.equals(prevMatrix)) return;

            prevMatrix.set(matrix);

            matrix.getTranslation(position);
            matrix.getScale(scale);
            matrix.getUnnormalizedRotation(rotation);

            mat4f.identity();

            double x1 = display.getX(), y1 = display.getY(), z1 = display.getZ();
            double x2 = position.x(), y2 = position.y(), z2 = position.z();

            if (Vector3d.distanceSquared(x1, y1, z1, x2, y2, z2) > 256) {
                display.setPos(x2, y2, z2);
            } else {
                mat4f.translate((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
            }

            mat4f.rotate(rotation).scale((float) scale.x(), (float) scale.y(), (float) scale.z());

            transformation = new AffineTransformation(mat4f);
        }

        DisplayEntityAccess.setTransformation(display, transformation);
        DisplayEntityAccess.setStartInterpolation(display, 0);
    }
}
