package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class DisplayEntityUtil {

    private DisplayEntityUtil() {}

    public static void applyTransformation(DisplayEntity display, Matrix4dc matrix) {
        Vector3d position = new Vector3d();
        matrix.getTranslation(position);

        Vector3d scale = new Vector3d();
        matrix.getScale(scale);

        Quaternionf rotation = new Quaternionf();
        matrix.getUnnormalizedRotation(rotation);

        display.setPos(position.x(), position.y(), position.z());

        DisplayEntityAccess.setTransformation(display, new AffineTransformation(new Matrix4f()
                .scale((float) scale.x(), (float) scale.y(), (float) scale.z())
                .rotate(rotation)));
    }
}
