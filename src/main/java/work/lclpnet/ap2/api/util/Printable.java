package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.impl.util.math.Matrix3i;
import work.lclpnet.kibu.structure.BlockStructure;

public interface Printable {

    BlockStructure getStructure();

    Vec3i getPrintOffset();

    Matrix3i getPrintMatrix();
}
