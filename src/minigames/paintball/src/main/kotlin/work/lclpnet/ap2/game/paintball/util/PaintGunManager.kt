package work.lclpnet.ap2.game.paintball.util

import com.jme3.math.Vector3f
import it.unimi.dsi.fastutil.Pair
import net.minecraft.ChatFormatting.RED
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.kit.KitManager
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.ap2.game.paintball.kit.PaintGunKit
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.math.MathUtil.applySpread
import work.lclpnet.ap2.impl.util.math.MathUtil.randomUnitVec3d
import work.lclpnet.gaco.core.util.ThreadUtil.executeOn
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.physics.EntityRefPhysicsElement
import work.lclpnet.gaco.scene.physics.SceneRigidBody
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.physics.api.PhysicsElement
import work.lclpnet.kibu.physics.api.event.collision.ElementCollisionEvents
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace
import work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread
import work.lclpnet.kibu.translate.Translations
import java.util.*
import java.util.function.BooleanSupplier
import kotlin.math.max

const val HIT_PAINT_RADIUS = 1.9

class PaintGunManager(
    private val world: ServerLevel,
    private val scene: Scene,
    val paintManager: PaintManager,
    private val teams: PaintballTeams,
    private val random: Random,
    private val participants: Participants,
    private val translations: Translations,
    private val debugController: DebugController,
    private val gameOver: BooleanSupplier
) {
    private val reloading = HashSet<UUID>()
    var shootingEnabled = false
    private var kitManager: KitManager? = null

    fun injectKitManager(kitManager: KitManager) {
        this.kitManager = kitManager
    }

    fun init(hooks: HookRegistrar) {
        MinecraftSpace.get(world).setCollisionEventsEnabled(true)

        ElementCollisionEvents.BLOCK_COLLISION.registerWith(hooks) { element, _, _ ->
            if (element is PaintballBullet) {
                onBulletHitTerrain(element)
            }
        }

        ElementCollisionEvents.ELEMENT_COLLISION.registerWith(hooks) { first, second, _ ->
            if (first is PaintballBullet && bulletCollision(first, second)) return@registerWith
            if (second is PaintballBullet && bulletCollision(second, first)) return@registerWith

            if (first is PaintballBullet && second is PaintballBullet
                && (first.playerContact || second.playerContact)
            ) {
                first.playerContact = true
                second.playerContact = true
                first.painting = false
                second.painting = false
            }
        }
    }

    private fun bulletCollision(bullet: PaintballBullet, other: PhysicsElement<*>): Boolean {
        if (bullet.ageTicks >= TEAM_COLLISION_ENABLE_TICKS) {
            bullet.startDespawnTimer()
        }

        if (other is EntityRefPhysicsElement) {
            other.cast().optional().ifPresent { entity ->
                onBulletHitEntity(bullet, entity)
            }
            return true
        }

        return false
    }

    private fun onBulletHitEntity(bullet: PaintballBullet, entity: Entity) {
        if (entity !is ServerPlayer || !participants.isParticipating(entity)) return

        bullet.playerContact = true
        bullet.painting = false

        if (bullet.isFading()) return

        bullet.startFading()
        bullet.forcePhysicsThread()

        val velocity = bullet.rigidBody.getLinearVelocity(Vector3f())

        if (velocity.lengthSquared() < 0.2f) return

        limitVelocity(bullet)

        val ownerUuid = bullet.owner ?: return

        world.server.execute {
            val owner = world.server.playerList.getPlayer(ownerUuid) ?: return@execute

            if (teams.teamManager.areTeamMates(owner, entity)) return@execute

            val bulletSettings = bullet.settings

            entity.hurtTime = 0
            entity.invulnerableTime = 0
            entity.hurtServer(world, entity.damageSources().source(DamageTypes.ARROW, owner, owner), bulletSettings.damage)

            paintAt(bullet, entity.x, entity.y, entity.z, HIT_PAINT_RADIUS, true)
        }
    }

    private fun onBulletHitTerrain(bullet: PaintballBullet) {
        bullet.startDespawnTimer()
        limitVelocity(bullet)

        if (gameOver.asBoolean || !bullet.painting) return

        bullet.onHit()

        val hit = bullet.rigidBody.frame.getLocation(Vector3f(), 1f)

        executeOn(world.server) {
            paintAt(
                bullet,
                hit.x.toDouble(),
                hit.y.toDouble(),
                hit.z.toDouble(),
                bullet.settings.paintRadius.toDouble(),
                true
            )
        }
    }

    fun paintAt(bullet: PaintballBullet, x: Double, y: Double, z: Double, radius: Double, shouldCount: Boolean) {
        val owner = participants.getParticipant(bullet.owner).orElse(null) ?: return
        val team = teams.teamOf(owner).orElse(null) ?: return

        val key: DyeTeamKey = team.key()

        val settings = bullet.settings
        val playerDeficit = teams.playerDeficit(team)
        val effectiveRadius = radius * (1f + playerDeficit * settings.deficitPaintBoost)

        val box = AABB.ofSize(Vec3(x, y, z), effectiveRadius * 2, effectiveRadius * 2, effectiveRadius * 2)

        for (pos in BlockBox.of(box)) {
            val dx = pos.x + 0.5 - x
            val dy = pos.y + 0.5 - y
            val dz = pos.z + 0.5 - z

            val inRange = dx * dx + dy * dy + dz * dz <= effectiveRadius * effectiveRadius

            if (inRange && tryPaint(key, pos, x, y, z) && shouldCount) {
                bullet.onHit()
            }
        }
    }

    fun limitVelocity(bullet: PaintballBullet) {
        bullet.forcePhysicsThread()

        val rigidBody: SceneRigidBody = bullet.rigidBody
        val velocity = Vector3f()
        rigidBody.getLinearVelocity(velocity)

        val maxPower = bullet.settings.maxImpactPower

        if (velocity.lengthSquared() > maxPower * maxPower) {
            rigidBody.setLinearVelocity(velocity.normalize().mult(maxPower))
        }
    }

    private fun tryPaint(teamKey: DyeTeamKey, blockPos: BlockPos, x: Double, y: Double, z: Double): Boolean {
        if (!paintManager.replace(blockPos, teamKey)) return false

        world.sendParticles(DustParticleOptions(teamKey.color(), 0.5f), x, y, z, 10, 0.2, 0.2, 0.2, 0.1)

        return true
    }

    fun shoot(player: ServerPlayer, paintGun: PaintGun, stack: ItemStack) {
        if (!shootingEnabled || player.cooldowns.isOnCooldown(stack) || isReloading(player)) return

        if (stack.damageValue >= stack.maxDamage) {
            translations.translateText("game.ap2.paintball.no_ink").formatted(RED).sendTo(player, true)
            player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.2f, 2f)
            return
        }

        val state = getPaintBulletState(player).orElse(null) ?: return

        player.cooldowns.addCooldown(stack, paintGun.cooldownTicks)
        stack.set(DataComponents.DAMAGE, stack.damageValue + 1)

        repeat(paintGun.bulletCount) {
            spawnPaintBulletWithSpread(player, paintGun, state)
        }

        val fireSound = paintGun.fireSound

        world.playSound(null, player.x, player.eyeY, player.z, fireSound.sound, SoundSource.PLAYERS, fireSound.volume, fireSound.pitch)
        world.sendParticles(ParticleTypes.SMOKE, player.x, player.eyeY, player.z, 2, 0.3, 0.3, 0.3, 0.2)
    }

    fun getPaintBulletState(player: ServerPlayer): Optional<BlockState> =
        teams.teamOf(player)
            .map { it.key() }
            .map { paintManager.getPaintBulletState(it as DyeTeamKey) }

    fun spawnPaintBulletWithSpread(player: ServerPlayer, paintGun: PaintGun, state: BlockState) {
        val bulletSettings = paintGun.bullet
        val scale = bulletSettings.size

        val dir = applySpread(player.lookAngle, Math.toRadians(paintGun.bulletSpread), random)
        val pos = getProjectileSpawn(player, dir, scale)

        executeOn(PhysicsThread.get(world)) {
            spawnPaintBullet(player, state, bulletSettings, pos, dir)
        }
    }

    fun spawnPaintBullet(player: ServerPlayer, state: BlockState, bulletSettings: PaintGun.BulletSettings, pos: Vec3, dir: Vec3) {
        val obj = PaintballBullet(scene, state, player.level(), bulletSettings, this, debugController)
        obj.position.set(pos.x(), pos.y(), pos.z())
        obj.scale.set(bulletSettings.size)
        obj.owner = player.uuid

        val rigidBody: SceneRigidBody = obj.rigidBody

        obj.updateRigidBody(rigidBody)

        val velocity = getProjectileVelocity(dir, bulletSettings)

        rigidBody.setLinearVelocity(toBullet(velocity))
        rigidBody.setAngularVelocity(toBullet(randomUnitVec3d(random)))
        rigidBody.setPhysicsLocation(toBullet(pos))
        rigidBody.setCollisionGroup(teams.bulletGroup(player))
        rigidBody.setCollideWithGroups(teams.bulletCollisionFlags(player))

        scene.add(obj)
    }

    fun getProjectileSpawn(player: ServerPlayer, dir: Vec3, projectileSize: Double): Vec3 {
        val spawnDist = 1.4

        val hit = RayCastUtil.raycast(
            player.level(),
            player.eyePosition,
            dir,
            spawnDist,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.ANY,
            CollisionContext.empty()
        ) { !it.isSpectator }

        var pos = hit.location

        if (hit is BlockHitResult) {
            pos = pos.add(hit.direction.unitVec3.scale(0.5 * projectileSize))
        } else if (hit.type != HitResult.Type.MISS) {
            pos = pos.add(dir.scale(-0.5 * projectileSize))
        }

        return pos
    }

    private fun getProjectileVelocity(dir: Vec3, bulletSettings: PaintGun.BulletSettings): Vec3 {
        val basePower = bulletSettings.power
        val minPowerScale = 0.65
        val maxPowerScale = 1.0

        val verticalComponent = max(0.0, dir.y)
        val powerScale = maxPowerScale + (minPowerScale - maxPowerScale) * verticalComponent

        return dir.scale(basePower * powerScale)
    }

    fun getPaintGunAndStack(player: ServerPlayer): Optional<Pair<PaintGun, ItemStack>> {
        val kitManager = this.kitManager ?: return Optional.empty()

        for (stack in player.inventory) {
            val kit = SingleItemKit.get(stack, kitManager) as? PaintGunKit ?: continue

            return Optional.of(Pair.of(kit.paintGun, stack))
        }

        return Optional.empty()
    }

    fun setReloading(player: ServerPlayer) {
        reloading.add(player.uuid)
    }

    fun removeReloading(player: ServerPlayer) {
        reloading.remove(player.uuid)
    }

    fun isReloading(player: ServerPlayer): Boolean = reloading.contains(player.uuid)

    fun refillPaintGun(stack: ItemStack) {
        stack.set(DataComponents.DAMAGE, 0)
    }

    fun refillPaintGun(player: ServerPlayer) {
        getPaintGunAndStack(player).ifPresent {
            refillPaintGun(it.right())
        }
    }
}
