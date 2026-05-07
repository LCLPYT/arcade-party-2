package work.lclpnet.ap2.ext.mc

import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.map.MapUtil

fun Vec3.centered() = Vec3(
    MapUtil.centeredDouble(x),
    MapUtil.centeredDouble(y),
    MapUtil.centeredDouble(z),
)

operator fun Vec3.plus(other: Vec3) =
    Vec3(this.x + other.x, this.y + other.y, this.z + other.z)

operator fun Vec3.minus(other: Vec3) =
    Vec3(this.x - other.x, this.y - other.y, this.z - other.z)

operator fun Vec3.times(scalar: Double) =
    Vec3(this.x * scalar, this.y * scalar, this.z * scalar)

operator fun Vec3.div(scalar: Double) =
    Vec3(this.x / scalar, this.y / scalar, this.z / scalar)

operator fun Double.times(vec: Vec3) =
    Vec3(vec.x * this, vec.y * this, vec.z * this)

operator fun Vec3.unaryMinus() =
    Vec3(-this.x, -this.y, -this.z)