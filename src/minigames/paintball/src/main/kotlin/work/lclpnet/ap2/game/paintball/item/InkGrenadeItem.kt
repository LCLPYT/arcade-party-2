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
import work.lclpnet.ap2.game.paintball.util.*
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.SoundHelper.playSound
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

    private val bulletSettings = PaintGun.BulletSettings(
        size = 0.08,
        power = 16.0,
        maxHits = 2.0,
        despawnSeconds = 2.0,
        mass = 0.01f,
        damage = 0.5f,
        maxImpactPower = 2f,
        paintRadius = 1.6f,
        deficitPaintBoost = 0f,
        split = NO_SPLIT
    )

    override fun id() = "ink_grenade"

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

        playSound(world, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8f, 1.2f)

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

                val newState: BlockState = if (flash) Blocks.TNT.defaultBlockState() else Blocks.WHITE_CONCRETE.defaultBlockState()
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
            val state = paintGunManager.getPaintBulletState(player).orElse(null) ?: return
            val team = teams.teamOf(player).orElse(null) ?: return

            val pos = Vec3(position.x, position.y, position.z)

            paintGunManager.paintManager.createExplosion(player, pos, team, EXPLOSION_POWER)

            executeOn(PhysicsThread.get(world)) {
                spawnFragments(pos, player, state)
            }
        }

        private fun spawnFragments(pos: Vec3, player: ServerPlayer, state: BlockState) {
            for (offset in MathUtil.fibonacciHemisphere(EXPLOSION_FRAGMENTS)) {
                val dir = Vec3(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
                val fragPos = pos.add(dir.scale(FRAGMENT_SPAWN_RADIUS.toDouble()))
                paintGunManager.spawnPaintBullet(player, state, bulletSettings, fragPos, dir)
            }
        }
    }
}
