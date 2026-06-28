package work.lclpnet.ap2.game.guess_it.util

import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.math.shape.*
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape.WithHeight
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape.WithRadius
import java.util.*
import kotlin.math.*

class BlockCountShapeManager<S>(
    private val random: Random,
    private val stage: S
) where S : BlockShape, S : WithHeight, S : WithRadius {

    private val shapesById = HashMap<String, ShapeProvider>()
    private val shapes = ArrayList<ShapeProvider>()

    init {
        registerShapes()
    }

    private fun registerShapes() {
        val maxRadius = min(stage.height() / 2, stage.radius())
        val maxSquareRadius = floor(sin(Math.PI * 0.25) * stage.radius()).toInt()
        val center = Vec3.atCenterOf(stage.center())
        val origin = Vec3.atCenterOf(stage.origin())

        register("cuboid") {
            val minRadius = 4
            val width = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius))
            val height = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius))
            val length = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius))
            Cuboid(origin.add(0.0, height * 0.5, 0.0), width.toDouble(), height.toDouble(), length.toDouble())
        }

        register("cube") {
            val minRadius = 4
            val radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius))
            Cube(origin.add(0.0, radius.toDouble(), 0.0), radius.toDouble())
        }

        register("ellipsoid") {
            val minRadius = 4
            val a = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val b = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val c = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Ellipsoid(center, a.toDouble(), b.toDouble(), c.toDouble())
        }

        register("sphere") {
            val minRadius = 4
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Sphere(center, radius.toDouble())
        }

        register("cone") {
            val minRadius = 4
            val minHeight = 10
            val maxHeight = stage.height()

            val radius = minRadius + random.nextInt(max(1, maxRadius - minRadius))
            val height = minHeight + random.nextInt(max(1, maxHeight - minHeight))
            Cone(origin, radius.toDouble(), height.toDouble())
        }

        register("cylinder") {
            val minRadius = 4
            val minHeight = 8
            val maxHeight = stage.height()

            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val height = minHeight + random.nextInt(max(1, maxHeight - minHeight))
            Cylinder(origin, radius.toDouble(), height.toDouble())
        }

        register("hemisphere") {
            val minRadius = 4
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val normal = MathUtil.randomUnitVec3d(random)
            Hemisphere(center, radius.toDouble(), normal)
        }

        register("pyramid") {
            val minRadius = 4
            val radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius))
            Pyramid(origin, radius.toDouble(), radius - 0.5)
        }

        register("prism") {
            val minRadius = 5
            val minHeight = 8
            val maxHeight = stage.height()
            val minAngle = Math.toRadians(20.0)

            val r1 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val r2 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            val r3 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))

            val height = minHeight + random.nextInt(max(1, maxHeight - minHeight))

            val alpha = random.nextDouble() * Math.PI
            val beta = alpha + minAngle + random.nextDouble() * (Math.PI - 2 * minAngle)
            val gamma = (alpha + beta) / 2 + Math.PI

            val v1 = origin.add(sin(alpha) * r1, 0.0, cos(alpha) * r1)
            val v2 = origin.add(sin(beta) * r2, 0.0, cos(beta) * r2)
            val v3 = origin.add(sin(gamma) * r3, 0.0, cos(gamma) * r3)
            Prism(v1, v2, v3, height.toDouble(), Vec3(0.0, 1.0, 0.0))
        }

        register("torus") {
            val minMinorRadius = 2
            val maxMinorRadius = 5

            val minorRadius = minMinorRadius + random.nextInt(maxMinorRadius - minMinorRadius + 1)

            val maxMajorRadius = maxRadius - minorRadius
            val minMajorRadius = minorRadius + 3

            val majorRadius = minMajorRadius + random.nextInt(maxMajorRadius - minMajorRadius + 1)

            val maxTilt = Math.PI / 5

            val rotation = Quaterniond()
                .rotateZ(random.nextDouble() * 2 * maxTilt - maxTilt)
                .rotateY(random.nextDouble() * Math.PI)
                .rotateX(Math.PI / 2)

            Torus(center, majorRadius.toDouble(), minorRadius.toDouble(), rotation)
        }

        register("tetrahedron") {
            val minRadius = 6
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Tetrahedron(origin.add(0.0, radius / 3.0, 0.0), radius.toDouble())
        }

        register("octahedron") {
            val minRadius = 4
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Octahedron(center, radius.toDouble())
        }

        register("icosahedron") {
            val minRadius = 4
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Icosahedron(center, radius.toDouble())
        }

        register("dodecahedron") {
            val minRadius = 4
            val radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)))
            Dodecahedron(center, radius.toDouble())
        }
    }

    private fun register(id: String, provider: ShapeProvider) {
        check(!shapesById.containsKey(id)) { "Duplicate shape id \"$id\"" }

        shapesById[id] = provider
        shapes.add(provider)
    }

    // TODO use restorable queue
    fun randomShape(): Shape =
        shapes[random.nextInt(shapes.size)].provide()

    fun getShape(id: String): Shape? {
        val shape = shapesById[id]

        return shape?.provide()
    }

    fun getShapes(): Set<String> =
        shapesById.keys.toSet()

    fun distance(shape: Shape, x: Double, y: Double, z: Double): Double {
        val distanceFunction = distanceFunction(shape)
        val center = shape.center()

        return distanceFunction.distanceTo(x - center.x(), y - center.y(), z - center.z())
    }

    fun distanceFunction(shape: Shape): DistanceFunction {
        if (shape is Cube || shape is Tetrahedron) {
            return { x, y, z -> chebyshevDist(x, y, z) }
        }

        if (shape is SphereBoundedShape || shape is Ellipsoid) {
            return { x, y, z -> euclideanDist(x, y, z) }
        }

        if (shape is Torus) {
            return { x, y, z ->
                val localPoint = Vector3d(x, y, z)
                shape.rotation().transformInverse(localPoint)

                val qx = sqrt(localPoint.x * localPoint.x + localPoint.z * localPoint.z)
                val ringDist = abs(qx - shape.majorRadius())

                sqrt(ringDist * ringDist + localPoint.y * localPoint.y)
            }
        }

        if (shape is Pyramid) {
            return { _, y, _ ->
                (y + shape.center().y()) - shape.origin().y()
            }
        }

        return { x, y, z -> chebyshevDist(x, y, z) }
    }

    private fun chebyshevDist(x: Double, y: Double, z: Double): Double =
        max(abs(x), max(abs(y), abs(z)))

    private fun euclideanDist(x: Double, y: Double, z: Double): Double =
        sqrt(x * x + y * y + z * z)

    fun interface DistanceFunction {
        fun distanceTo(x: Double, y: Double, z: Double): Double
    }

    private fun interface ShapeProvider {
        fun provide(): Shape
    }
}
