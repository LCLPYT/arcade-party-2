package work.lclpnet.ap2

import net.minecraft.util.math.Vec3d
import work.lclpnet.kibu.hook.util.PositionRotation

fun PositionRotation.asVec3d(): Vec3d = Vec3d(x, y, z)
