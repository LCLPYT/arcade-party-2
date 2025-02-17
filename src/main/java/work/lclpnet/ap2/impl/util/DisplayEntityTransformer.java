package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.*;

public class DisplayEntityTransformer {

    private final Vector3d position = new Vector3d();
    private final Vector3d translation = new Vector3d();
    private final Vector3d scale = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private final Matrix4f mat4f = new Matrix4f();
    private final Matrix4d prevMatrix = new Matrix4d();
    private AffineTransformation transformation = new AffineTransformation(mat4f);

    public synchronized void update(Matrix4dc matrix, double x, double y, double z) {
        if (matrix.equals(prevMatrix)) return;

        prevMatrix.set(matrix);

        matrix.getTranslation(translation);
        matrix.getScale(scale);
        matrix.getUnnormalizedRotation(rotation);

        mat4f.identity();

        double tx = translation.x(), ty = translation.y(), tz = translation.z();

        if (Vector3d.distanceSquared(x, y, z, tx, ty, tz) > 256) {
            position.set(tx, ty, tz);
        } else {
            mat4f.translate((float) (tx - x), (float) (ty - y), (float) (tz - z));
        }

        mat4f.rotate(rotation).scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        transformation = new AffineTransformation(mat4f);
    }

    public void applyTransformation(DisplayEntity display, Matrix4dc matrix) {
        update(matrix, display.getX(), display.getY(), display.getZ());
        applyTransformation(display);
    }

    public void applyTransformation(DisplayEntity display) {
        display.setPos(position.x, position.y, position.z);
        display.setTransformation(transformation);
        display.setStartInterpolation(0);
    }
}
