package work.lclpnet.ap2.ext

import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.map.MapUtil.centeredDouble
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.hook.util.PositionRotation

fun PositionRotation.asVec3d(): Vec3 = Vec3(x(), y(), z())

fun PositionRotation.transform(mat4: AffineIntMatrix): PositionRotation = MathUtil.transform(this, mat4)

fun PositionRotation.centered() = PositionRotation(
    centeredDouble(x()),
    centeredDouble(y()),
    centeredDouble(z()),
    yaw,
    pitch
)