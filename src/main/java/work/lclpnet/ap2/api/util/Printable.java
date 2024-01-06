package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.Vec3i;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.math.Matrix3i;

public interface Printable {

    BlockStructure getStructure();

    Vec3i getPrintOffset();

    Matrix3i getPrintMatrix();
}
