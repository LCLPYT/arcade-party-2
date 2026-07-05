package work.lclpnet.ap2.game.paintball.util

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
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.kit.KitManager
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.ap2.game.paintball.kit.PaintGunKit
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.math.MathUtil.applySpread
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.kibu.translate.Translations
import java.util.*
import java.util.function.BooleanSupplier
import java.util.function.Predicate

const val HIT_PAINT_RADIUS = 1.9

class PaintGunManager(
    private val world: ServerLevel,
    private val scene: Scene,
    val paintManager: PaintManager,
    private val teams: PaintballTeams,
    private val random: Random,
    private val participants: Participants,
    private val translations: Translations,
    private val gameOver: BooleanSupplier
) {
    private val reloading = HashSet<UUID>()
    var shootingEnabled = false
    private var kitManager: KitManager? = null

    val sniperCharge = SniperChargeManager(this)

    fun injectKitManager(kitManager: KitManager) {
        this.kitManager = kitManager
    }

    /**
     * Paints a spherical splat of the owner's team color, centered at the given world position.
     * @param owner The player that fired the ink.
     * @param point The center of the splat.
     * @param radius The base paint radius, in blocks.
     * @param deficitBoost How much to boost the radius per missing teammate (multiplier).
     */
    fun splat(owner: UUID, point: Vec3, radius: Float, deficitBoost: Float) {
        if (gameOver.asBoolean) return

        val painter = participants.getParticipant(owner) ?: return
        val team = teams.teamOf(painter) ?: return

        val key: DyeTeamKey = team.key
        val effectiveRadius = (radius * (1f + teams.playerDeficit(team) * deficitBoost)).toDouble()

        val x = point.x
        val y = point.y
        val z = point.z

        val box = AABB.ofSize(Vec3(x, y, z), effectiveRadius * 2, effectiveRadius * 2, effectiveRadius * 2)

        for (pos in BlockBox.of(box)) {
            val dx = pos.x + 0.5 - x
            val dy = pos.y + 0.5 - y
            val dz = pos.z + 0.5 - z

            if (dx * dx + dy * dy + dz * dz <= effectiveRadius * effectiveRadius) {
                tryPaint(key, pos, x, y, z, painter)
            }
        }
    }

    /**
     * Applies an ink hit to an enemy player: deals damage and paints a splat around them.
     */
    fun inkHitEntity(owner: UUID, target: Entity, damage: Float, deficitBoost: Float) {
        if (target !is ServerPlayer || !participants.isParticipating(target)) return

        val ownerPlayer = world.server.playerList.getPlayer(owner) ?: return

        if (teams.teamManager.areTeamMates(ownerPlayer, target)) return

        target.hurtTime = 0
        target.invulnerableTime = 0
        target.hurtServer(world, target.damageSources().source(DamageTypes.ARROW, ownerPlayer, ownerPlayer), damage)

        splat(owner, Vec3(target.x, target.y, target.z), HIT_PAINT_RADIUS.toFloat(), deficitBoost)
    }

    private fun tryPaint(teamKey: DyeTeamKey, blockPos: BlockPos, x: Double, y: Double, z: Double, painter: ServerPlayer): Boolean {
        if (!paintManager.replace(blockPos, teamKey, painter)) return false

        world.sendParticles(DustParticleOptions(teamKey.color, 0.5f), x, y, z, 10, 0.2, 0.2, 0.2, 0.1)

        return true
    }

    fun shoot(player: ServerPlayer, paintGun: PaintGun, stack: ItemStack) {
        if (!shootingEnabled || player.cooldowns.isOnCooldown(stack) || isReloading(player)) return

        if (!hasAmmo(player)) {
            notifyNoAmmo(player)
            return
        }

        applyCooldown(player, stack, paintGun)

        repeat(paintGun.bulletCount) {
            spawnInkProjectileWithSpread(player, paintGun)
        }

        onFire(paintGun, player)
    }

    /**
     * Fires a single charged shot with the given, charge scaled [settings]. Reuses the same ammo,
     * cooldown and feedback handling as [shoot], but spawns exactly one ink projectile along the
     * player's look direction.
     */
    fun shootCharged(player: ServerPlayer, paintGun: PaintGun, stack: ItemStack, settings: InkSettings) {
        if (!shootingEnabled || player.cooldowns.isOnCooldown(stack) || isReloading(player)) return

        if (stack.damageValue >= stack.maxDamage) {
            notifyNoAmmo(player)
            return
        }

        applyCooldown(player, stack, paintGun)

        val dir = player.lookAngle
        val pos = getProjectileSpawn(player, dir, settings.blobRadius)
        spawnInkProjectile(player, settings, pos, dir)

        onFire(paintGun, player)
    }

    private fun onFire(paintGun: PaintGun, player: ServerPlayer) {
        val fireSound = paintGun.fireSound

        world.playSound(
            null,
            player.x,
            player.eyeY,
            player.z,
            fireSound.sound,
            SoundSource.PLAYERS,
            fireSound.volume,
            fireSound.pitch
        )

        world.sendParticles(ParticleTypes.SMOKE, player.x, player.eyeY, player.z, 2, 0.3, 0.3, 0.3, 0.2)
    }

    private fun applyCooldown(
        player: ServerPlayer,
        stack: ItemStack,
        paintGun: PaintGun
    ) {
        player.cooldowns.addCooldown(stack, paintGun.cooldownTicks)
        stack.set(DataComponents.DAMAGE, stack.damageValue + 1)
    }

    fun notifyNoAmmo(player: ServerPlayer) {
        translations.translateText("no_ink").withStyle(RED).sendTo(player, true)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.2f, 2f)
    }

    fun getPaintBulletState(player: ServerPlayer): BlockState? =
        teams.teamOf(player)?.key?.let {
            paintManager.getPaintBulletState(it)
        }

    fun spawnInkProjectileWithSpread(player: ServerPlayer, paintGun: PaintGun) {
        val settings = paintGun.ink

        val dir = applySpread(player.lookAngle, Math.toRadians(paintGun.bulletSpread), random)
        val pos = getProjectileSpawn(player, dir, settings.blobRadius)

        spawnInkProjectile(player, settings, pos, dir)
    }

    fun spawnInkProjectile(player: ServerPlayer, settings: InkSettings, pos: Vec3, dir: Vec3) {
        val teamKey = teams.teamOf(player)?.key ?: return
        val state = paintManager.getPaintBulletState(teamKey)

        val projectile = InkProjectile(
            scene,
            world,
            settings,
            player.uuid,
            teamKey,
            this,
            enemyFilter(player),
            pos,
            dir,
            random
        )

        scene.add(projectile)
    }

    private fun enemyFilter(shooter: ServerPlayer): Predicate<Entity> {
        val shooterId = shooter.uuid
        val shooterTeam = teams.teamOf(shooter)?.key

        return Predicate { entity ->
            entity is ServerPlayer && entity.uuid != shooterId
                    && participants.isParticipating(entity)
                    && teams.teamOf(entity)?.key != shooterTeam
        }
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

    fun isReloading(player: ServerPlayer): Boolean =
        reloading.contains(player.uuid)

    fun refillPaintGun(stack: ItemStack) {
        stack.set(DataComponents.DAMAGE, 0)
    }

    fun refillPaintGun(player: ServerPlayer) {
        getPaintGunAndStack(player).ifPresent {
            refillPaintGun(it.right())
        }
    }

    fun hasAmmo(player: ServerPlayer): Boolean {
        val pair = getPaintGunAndStack(player).orElse(null) ?: return false

        val stack = pair.right()

        return stack.damageValue < stack.maxDamage
    }
}
