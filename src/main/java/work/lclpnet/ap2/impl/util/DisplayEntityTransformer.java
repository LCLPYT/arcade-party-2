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
    private final Matrix4d prevMatrix = new Matrix4d().scale(Double.NaN);
    private AffineTransformation transformation = new AffineTransformation(mat4f);

    public synchronized boolean update(Matrix4dc matrix) {
        if (matrix.equals(prevMatrix)) return false;

        prevMatrix.set(matrix);

        matrix.getTranslation(translation);
        matrix.getScale(scale);
        matrix.getUnnormalizedRotation(rotation);

        mat4f.identity();

        position.set(translation.x(), translation.y(), translation.z());

        mat4f.rotate(rotation).scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        transformation = new AffineTransformation(mat4f);

        return true;
    }

    public void updateAndApply(DisplayEntity display, Matrix4dc matrix) {
        if (update(matrix)) {
            apply(display);
        }
    }

    public void apply(DisplayEntity display) {
        if (display.squaredDistanceTo(position.x, position.y, position.z) > 1.0E-4) {
            display.setPos(position.x, position.y, position.z);
        }

        display.setTransformation(transformation);
        display.setStartInterpolation(0);
    }
}
