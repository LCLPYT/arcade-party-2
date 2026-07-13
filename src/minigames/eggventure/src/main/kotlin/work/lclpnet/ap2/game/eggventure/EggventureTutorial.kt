package work.lclpnet.ap2.game.eggventure

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.gaco.core.api.Resolvable
import work.lclpnet.gaco.dynamic_entities.DynamicEntity
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.scene.MixedMountContext
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.`object`.ItemDisplayObject
import work.lclpnet.gaco.scene.`object`.PlayerTextDisplayObject
import work.lclpnet.gaco.scene.util.WorldPosSync
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import java.util.*

private const val EGG_SWITCH_TICKS = 10
private const val PLAYER_DIST = 2.5
private const val EGG_RADIUS = 0.25

class EggventureTutorial(
    private val world: ServerLevel,
    dynamicEntityManager: DynamicEntityManager,
    private val random: Random,
    private val translations: Translations
) {
    private val scene = Scene(MixedMountContext(world, dynamicEntityManager))
    private val variants = eggVariants(world.registryAccess())
    private val eggs = ArrayList<TutorialEgg>()

    fun start(scheduler: TaskScheduler, participants: Participants): AutoCloseable {
        if (variants.isEmpty()) {
            throw IllegalStateException("There are no egg variants defined")
        }

        for (player in participants) {
            val variant = variants[random.nextInt(variants.size)]
            startTutorial(player, variant)
        }

        scene.animate(1, scheduler)

        var t = 0
        val switcher = scheduler.interval(Runnable {
            if (++t % EGG_SWITCH_TICKS == 0) {
                switchEggVariants()
            }
        }, 1)

        return AutoCloseable {
            switcher.cancel()
            scene.clear()
            scene.stopAnimation()
        }
    }

    private fun switchEggVariants() {
        for (egg in eggs) {
            val variant = variants[random.nextInt(variants.size)]
            egg.setStack(variant.createStack())
        }
    }

    private fun startTutorial(player: ServerPlayer, variant: work.lclpnet.ap2.api.util.heads.PlayerHead) {
        val uuid = player.uuid
        val text = translations.translateText(player, "find_sample").withStyle(ChatFormatting.GREEN)

        val egg = TutorialEgg(scene, variant) { world.server.playerList.getPlayer(uuid) }
        val label = PlayerTextDisplayObject(scene, text, player)
        label.position.set(0.0, 0.1, 0.0)
        label.scale.set(0.65)
        label.setBillboardMode(Display.BillboardConstraints.CENTER)

        egg.addChild(label)
        scene.add(egg)
        eggs.add(egg)
    }
}

private class TutorialEgg(
    scene: Scene,
    variant: work.lclpnet.ap2.api.util.heads.PlayerHead,
    private val playerRef: Resolvable<ServerPlayer>
) : ItemDisplayObject(scene, variant.createStack()), DynamicEntity, Animatable {

    private val posSync = WorldPosSync()

    override fun updateMatrixWorld(withParent: Boolean, withChildren: Boolean) {
        super.updateMatrixWorld(withParent, withChildren)
        posSync.update(matrixWorld)
    }

    override fun getPosition(): Vec3 = posSync.mcWorldPos()

    override fun getEntity(player: ServerPlayer): Entity? {
        val owner = playerRef.optional().orElse(null)
        if (owner == null || player != owner) return null
        return entityRef.resolve()
    }

    override fun cleanup(player: ServerPlayer) {}

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        val player = playerRef.optional().orElse(null) ?: return

        val hit = RayCastUtil.raycast(
            player.level(), player.eyePosition, player.lookAngle, PLAYER_DIST,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, CollisionContext.empty()
        ) { !it.isSpectator }

        var pos = hit.location

        if (hit is BlockHitResult) {
            pos = pos.add(hit.direction.unitVec3.scale(EGG_RADIUS))
        } else if (hit.type != HitResult.Type.MISS) {
            pos = pos.add(player.lookAngle.scale(-EGG_RADIUS))
        }

        position.set(pos.x(), pos.y() + EGG_RADIUS, pos.z())
    }
}
