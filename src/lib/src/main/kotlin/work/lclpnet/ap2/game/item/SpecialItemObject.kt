package work.lclpnet.ap2.game.item

import com.mojang.math.Axis
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Display
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.`object`.ItemDisplayObject
import work.lclpnet.gaco.scene.`object`.TranslatedTextDisplayObject
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText
import kotlin.math.max

class SpecialItemObject @JvmOverloads constructor(
    scene: Scene,
    private val item: SpecialItem,
    stack: ItemStack,
    translations: Translations,
    name: TranslatedText,
    private val size: Double = 0.25,
) : Object3d(scene), Animatable {
    private val ageOffset = (Math.random() * Math.PI * 2).toFloat()
    private val itemDisplay: ItemDisplayObject
    private val textDisplay: TranslatedTextDisplayObject
    private var age = 0.0
    private var boundingBox: AABB? = null

    var pickedUp = false
        private set

    private var pickupAnimation: PickupAnimation? = null

    var pickupDelay = 0.0
        private set

    init {
        val sizeScale: Double = size / DEFAULT_SIZE

        itemDisplay = ItemDisplayObject(scene, stack)
        itemDisplay.scale.set(sizeScale)
        itemDisplay.setItemDisplayContext(ItemDisplayContext.GROUND)

        addChild(itemDisplay)

        textDisplay = TranslatedTextDisplayObject(scene, translations)
        textDisplay.position.set(0.0, size * 2, 0.0)
        textDisplay.scale.set(0.6 * sizeScale)
        textDisplay.controller().text = name
        textDisplay.controller().billboardMode = Display.BillboardConstraints.CENTER

        addChild(textDisplay)

        updateBoundingBox()
    }

    fun itemDisplay(): ItemDisplayObject = itemDisplay

    fun item(): SpecialItem = item

    fun boxAt(x: Double, y: Double, z: Double): AABB = AABB(
        x - size,
        y,
        z - size,
        x + size,
        y + 2 * size,
        z + size
    )

    private fun updateBoundingBox() {
        boundingBox = boxAt(position.x, position.y, position.z)
    }

    fun isOnGround(world: ServerLevel): Boolean {
        val box = boxAt(position.x, position.y - 0.05, position.z)

        return world.getBlockCollisions(null, box).iterator().hasNext()
    }

    override fun updateMatrixWorld(withParent: Boolean, withChildren: Boolean) {
        super.updateMatrixWorld(withParent, withChildren)

        updateBoundingBox()
    }

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        age += dt

        if (pickupDelay > 0) {
            pickupDelay = max(0.0, pickupDelay - dt)
        }

        if (pickupAnimation != null) {
            pickupAnimation!!.updateAnimation(dt, ctx)
            return
        }

        itemDisplay.rotation.set(Axis.YP.rotation(age.toFloat() + ageOffset))

        val offsetY =
            (Mth.sin((age.toFloat() * 2f + this.ageOffset).toDouble()) * 0.1f + 0.1f) + 0.25f * itemDisplay.scale.y

        itemDisplay.position.set(0.0, offsetY, 0.0)
        textDisplay.position.set(0.0, offsetY + 2 * size, 0.0)
    }

    fun intersects(box: AABB): Boolean =
        boundingBox!!.intersects(box)

    fun startPickup(player: ServerPlayer, whenDone: () -> Unit) {
        if (pickedUp) return

        pickedUp = true

        pickupAnimation = PickupAnimation(
            this,
            { target -> target.set(player.x, player.y, player.z) },
            whenDone
        )
    }

    fun setPickupDelay(delayTicks: Int) {
        pickupDelay = delayTicks / 20.0
    }

    fun setGlowing(glowing: Boolean) {
        itemDisplay.setGlowing(glowing)
    }

    fun setGlowColorOverride(color: Int) {
        itemDisplay.glowColorOverride = color
    }

    companion object {
        const val DEFAULT_SIZE: Double = 0.25
    }
}
