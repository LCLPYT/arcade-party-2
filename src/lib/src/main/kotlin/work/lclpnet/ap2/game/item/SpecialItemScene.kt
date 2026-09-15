package work.lclpnet.ap2.game.item

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.math.solver.*
import work.lclpnet.gaco.scene.MixedMountContext
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import kotlin.math.pow

class SpecialItemScene(private val random: Random, world: ServerLevel) {

    private val scene: Scene
    private val objects = ArrayList<SpecialItemObject>()
    private val indices = Object2IntOpenHashMap<SpecialItemObject>()
    private val gravity: Gradient = SimpleGravityGradient(0.04 * 20.0.pow(2.0))
    private val solver: NumericalSolver = EulerSolver.INSTANCE
    private val onPickup: Hook<SpecialItemPickup> = HookFactory.createArrayBacked(
        SpecialItemPickup::class.java
    ) { hooks ->
        SpecialItemPickup { player, obj ->
            var pickup = false

            for (hook in hooks) {
                if (hook.shouldPickup(player, obj)) {
                    pickup = true
                }
            }
            pickup
        }
    }
    private val dynamicEntityManager = DynamicEntityManager(world)
    private val removal = ArrayList<SpecialItemObject>()
    private val minY: Double
    private var state = StateVector(arrayOfNulls<Vector3d>(0))

    init {
        this.scene = Scene(MixedMountContext(world, dynamicEntityManager))
        indices.defaultReturnValue(-1)
        minY = world.minY - 20.0
    }

    fun init(scheduler: TaskScheduler, hooks: HookRegistrar) {
        scene.animate(1, scheduler)

        scene.onUpdateAnimation { dt, ctx ->
            updateSimulation(dt, ctx)
        }

        dynamicEntityManager.init(scheduler, hooks)
    }

    @Synchronized
    fun velocity(obj: SpecialItemObject): Vector3d {
        val i = indices.getInt(obj)
        return if (i == -1) Vector3d(0.0) else state.getVector3(2 * i + 1)
    }

    @Synchronized
    private fun updateSimulation(dt: Double, ctx: AnimationContext) {
        solver.solve(state, dt, gravity)

        removal.clear()

        for (i in objects.indices) {
            val obj = objects.get(i)

            if (obj.pickedUp || obj.isOnGround(ctx.world())) {
                // reset velocity
                state.getVector3(2 * i + 1).set(0.0)
                continue
            }

            obj.position.set(state.getVector3(2 * i))

            if (obj.position.y < minY) {
                removal.add(obj)
            }
        }

        for (obj in removal) {
            remove(obj)
        }
    }

    @JvmOverloads
    fun spawnItem(
        pos: Vec3,
        item: SpecialItem,
        stack: ItemStack,
        translations: Translations,
        name: TranslatedText,
        itemSize: Double = SpecialItemObject.DEFAULT_SIZE
    ): SpecialItemObject {
        val obj = SpecialItemObject(scene, item, stack, translations, name, itemSize)
        obj.position.set(pos.x, pos.y, pos.z)

        scene.add(obj)

        synchronized(this) {
            indices.put(obj, objects.size)
            objects.add(obj)

            val size = state.size()
            val vectors = arrayOfNulls<Vector3d>(size + 2)

            for (i in 0..<size) {
                vectors[i] = state.getVector3(i)
            }

            vectors[size] = Vector3d(pos.x, pos.y, pos.z)
            vectors[size + 1] = Vector3d()
            state = StateVector(vectors)
        }

        return obj
    }

    fun remove(obj: SpecialItemObject) {
        scene.remove(obj)

        synchronized(this) {
            val i = indices.removeInt(obj)

            if (i == -1) return

            objects.removeAt(i)

            for (j in i..<objects.size) {
                indices.put(objects[j], j)
            }

            val size = state.size()
            val vectors = arrayOfNulls<Vector3d>(size - 2)

            for (j in 0..<i) {
                vectors[j] = state.getVector3(j)
            }

            for (j in i + 2..<size) {
                vectors[j - 2] = state.getVector3(j)
            }

            state = StateVector(vectors)
        }
    }

    fun tickPickUp(player: ServerPlayer) {
        if (player.isSpectator || player.health <= 0f) return

        val vehicle = player.vehicle

        val box = if (vehicle != null && !vehicle.isRemoved) {
            player.boundingBox.minmax(vehicle.boundingBox).inflate(1.0, 0.0, 1.0)
        } else {
            player.boundingBox.inflate(1.0, 0.5, 1.0)
        }

        for (obj in objects) {
            if (obj.pickedUp
                || obj.pickupDelay > 0
                || !obj.intersects(box)
                || !onPickup.invoker().shouldPickup(player, obj)
            ) continue

            obj.startPickup(player) {
                remove(obj)
            }

            val pitch = (random.nextFloat() - random.nextFloat()) * 1.4f + 2.0f

            player.level().playSound(
                null,
                obj.position.x,
                obj.position.y,
                obj.position.z,
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2f,
                pitch
            )
        }
    }

    fun onPickup(): Hook<SpecialItemPickup> = onPickup

    fun itemCount(): Int = objects.size

    operator fun contains(obj: SpecialItemObject?): Boolean =
        indices.containsKey(obj)

    fun interface SpecialItemPickup {
        fun shouldPickup(player: ServerPlayer, obj: SpecialItemObject): Boolean
    }
}
