package work.lclpnet.ap2

import net.minecraft.world.phys.Vec3
import work.lclpnet.kibu.hook.util.PositionRotation

fun PositionRotation.asVec3d(): Vec3 = Vec3(x(), y(), z())
