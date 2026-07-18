package work.lclpnet.ap2.game.paintball.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.hook.DeathMessageItemCallback
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.game.paintball.util.*
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d
import work.lclpnet.gaco.core.util.ThreadUtil.executeOn
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.physics.SceneRigidBody
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread
import java.util.*

private const val INK_GRENADE_SIZE = 0.3f
private const val THROW_POWER = 16f

private const val BLINK_SECONDS = 0.5
private const val FUSE_SECONDS = 4.0
private const val GRENADE_MASS = 0.8f
private const val FRAGMENT_SPAWN_RADIUS = 0.2f
private const val EXPLOSION_POWER = 4.5f
private const val EXPLOSION_FRAGMENTS = 50

class InkGrenadeItem(
    private val paintGunManager: PaintGunManager,
    private val scene: Scene,
    private val random: Random,
    private val teams: PaintballTeams,
    private val onUsed: (ServerPlayer) -> Unit
) : SpecialItem {

    private val inkSettings = InkSettings(
        speed = 16.0,
        gravity = 11.0,
        range = 6.0,
        blobCount = 1,
        blobRadius = 0.1,
        blobSpread = 0.0,
        splatRadius = 1.6f,
        damage = 0.5f,
        deficitPaintBoost = 0f,
        trail = NO_TRAIL
    )

    override val id = "ink_grenade"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.TNT)

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        DeathMessageItemCallback.HOOK.registerWith(hooks) { _, _, _ -> ItemStack.EMPTY }
    }

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        if (hand == InteractionHand.OFF_HAND) return InteractionResult.PASS

        throwInkGrenade(player, stack)

        return InteractionResult.SUCCESS_SERVER
    }

    override fun onSwing(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext) {
        throwInkGrenade(player, stack)
    }

    private fun throwInkGrenade(player: ServerPlayer, stack: ItemStack) {
        val world = player.level()

        executeOn(PhysicsThread.get(world)) {
            spawnObject(player)
        }

        SoundHelper.playSoundAt(player, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8f, 1.2f)

        stack.consume(1, player)

        onUsed(player)
    }

    private fun spawnObject(player: ServerPlayer) {
        val dir = player.lookAngle
        val pos = paintGunManager.getProjectileSpawn(player, dir, INK_GRENADE_SIZE.toDouble())

        val obj = InkGrenadeObject(scene, player.level())
        obj.position.set(pos.x(), pos.y(), pos.z())
        obj.scale.set(INK_GRENADE_SIZE.toDouble())
        obj.thrower = player.uuid

        val rigidBody: SceneRigidBody = obj.rigidBody

        rigidBody.setLinearVelocity(toBullet(dir.scale(THROW_POWER.toDouble())))
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)))
        rigidBody.setPhysicsLocation(toBullet(pos))
        rigidBody.setCollisionGroup(teams.bulletGroup(player))
        rigidBody.setCollideWithGroups(teams.bulletCollisionFlags(player))

        obj.updateRigidBody(rigidBody)

        scene.add(obj)
    }

    private inner class InkGrenadeObject(scene: Scene, world: ServerLevel) :
        PaintballProjectile(scene, Blocks.TNT.defaultBlockState(), world) {

        var thrower: UUID? = null
        private var blinkTimer = 0.0
        private var fuseTimer = FUSE_SECONDS
        private var flash = false

        init {
            rigidBody.setMass(GRENADE_MASS)
        }

        override fun updateAnimation(dt: Double, ctx: AnimationContext) {
            super.updateAnimation(dt, ctx)

            blinkTimer += dt
            fuseTimer -= dt

            if (blinkTimer >= BLINK_SECONDS) {
                blinkTimer -= BLINK_SECONDS

                val newState: BlockState = if (flash) Blocks.TNT.defaultBlockState() else Blocks.CONCRETE.white.defaultBlockState()
                flash = !flash

                setBlockState(newState)
                rigidBody.setMass(GRENADE_MASS)
            }

            if (fuseTimer > 0) return

            explode()
        }

        private fun explode() {
            detach()

            val player = world.server.playerList.getPlayer(thrower ?: return) ?: return
            paintGunManager.getPaintBulletState(player) ?: return
            val team = teams.teamOf(player) ?: return

            val pos = Vec3(position.x, position.y, position.z)

            paintGunManager.paintManager.createExplosion(player, pos, team, EXPLOSION_POWER)

            spawnFragments(pos, player)
        }

        private fun spawnFragments(pos: Vec3, player: ServerPlayer) {
            for (offset in MathUtil.fibonacciHemisphere(EXPLOSION_FRAGMENTS)) {
                val dir = Vec3(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
                val fragPos = pos.add(dir.scale(FRAGMENT_SPAWN_RADIUS.toDouble()))
                paintGunManager.spawnInkProjectile(player, inkSettings, fragPos, dir)
            }
        }
    }
}
