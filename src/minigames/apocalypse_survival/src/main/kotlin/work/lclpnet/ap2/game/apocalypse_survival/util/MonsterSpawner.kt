package work.lclpnet.ap2.game.apocalypse_survival.util

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Drowned
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.mixin.MobAccessor
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.apocalypse_survival.goal.RoamGoal
import work.lclpnet.ap2.game.apocalypse_survival.goal.UnstuckGoal
import work.lclpnet.ap2.impl.util.EntityUtil
import work.lclpnet.ap2.impl.util.GoalModifier
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.kibu.scheduler.Ticks
import java.util.Random
import kotlin.math.pow

private const val PARTICLE_TICKS = 12
private val MOB_MIN_TICKS = Ticks.seconds(1)
private val MOB_MAX_TICKS = Ticks.seconds(3) + 10
private const val MOB_LIMIT = 150

class MonsterSpawner(
    private val world: ServerLevel,
    private val stage: BlockShape,
    private val random: Random,
    private val targetManager: TargetManager
) {

    private val stageWithRadius = stage as BlockShape.WithRadius

    private val zombieTypes = WeightedList<EntityType<out Zombie>>().also {
        it.add(EntityType.ZOMBIE, 0.8f)
        it.add(EntityType.ZOMBIE_VILLAGER, 0.07f)
        it.add(EntityType.HUSK, 0.05f)
        it.add(EntityType.ZOMBIFIED_PIGLIN, 0.03f)
        it.add(EntityType.DROWNED, 0.05f)
    }
    private val skeletonTypes = WeightedList<EntityType<out AbstractSkeleton>>().also {
        it.add(EntityType.SKELETON, 0.8f)
        it.add(EntityType.WITHER_SKELETON, 0.05f)
        it.add(EntityType.BOGGED, 0.05f)
        it.add(EntityType.STRAY, 0.08f)
    }
    private val spawnTypes = WeightedList<SpawnType>()
    private var timeTicks = 0
    private var nextParticle = 0
    private var nextMob = scheduleNextMob()
    private var mobCount = 0

    fun tick() {
        val time = timeTicks++

        handleTimedEvents(time)

        if (nextParticle-- <= 0) {
            nextParticle = PARTICLE_TICKS
            spawnParticle()
        }

        if (mobCount < MOB_LIMIT && nextMob-- <= 0) {
            nextMob = scheduleNextMob()
            spawnMob()
        }
    }

    private fun handleTimedEvents(ticks: Int) {
        when (ticks) {
            0 -> spawnTypes.add(SpawnType.ZOMBIE, 0.6f)
            70 * 20 -> spawnTypes.add(SpawnType.VINDICATOR, 0.06f)
            130 * 20 -> spawnTypes.add(SpawnType.SKELETON, 0.165f)
            150 * 20 -> spawnTypes.add(SpawnType.GHAST, 0.05f)
            175 * 20 -> spawnTypes.add(SpawnType.EVOKER, 0.06f)
            200 * 20 -> spawnTypes.add(SpawnType.PHANTOM, 0.04f)
        }
    }

    private fun scheduleNextMob() = MOB_MIN_TICKS + random.nextInt(MOB_MAX_TICKS - MOB_MIN_TICKS + 1)

    private fun spawnParticle() {
        val center: BlockPos = stage.center()
        val offset = stageWithRadius.radius() / 2

        world.sendParticles(
            ParticleTypes.REVERSE_PORTAL,
            center.x + 0.5, center.y + 0.5, center.z + 0.5,
            30, offset.toDouble(), offset.toDouble(), offset.toDouble(), 0.15
        )
    }

    private fun spawnMob() {
        when (spawnTypes.getRandomElement(random)) {
            SpawnType.ZOMBIE -> spawnZombie()
            SpawnType.SKELETON -> spawnSkeleton()
            SpawnType.PHANTOM -> spawnPhantom()
            SpawnType.GHAST -> spawnGhast()
            SpawnType.VINDICATOR -> spawnVindicator()
            SpawnType.EVOKER -> spawnEvoker()
            null -> {}
        }
    }

    private fun spawnZombie() {
        val zombie = createMob(zombieTypes) ?: return

        zombie.setCanBreakDoors(true)

        var baseSpeed = zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)

        if (zombie.isBaby) {
            baseSpeed *= 0.75
        } else if (random.nextFloat() < 0.05f) {
            val scale = random.nextFloat(0.75f, 1.8f)
            EntityUtil.setAttribute(zombie, Attributes.SCALE, scale.toDouble())
            baseSpeed *= (1.0 / scale.toDouble().pow(1.15)).coerceIn(0.75, 1.12)
        }

        if (zombie is Drowned) {
            baseSpeed *= 0.87
        } else if (zombie is ZombifiedPiglin) {
            baseSpeed *= 0.9
        }

        EntityUtil.setAttribute(zombie, Attributes.MOVEMENT_SPEED, baseSpeed)

        val mobAccess = zombie as MobAccessor
        val goalSelector: GoalSelector = mobAccess.goalSelector

        GoalModifier.clear(goalSelector)
        GoalModifier.clear(mobAccess.targetSelector)

        goalSelector.addGoal(1, BreakDoorGoal(zombie) { true })
        goalSelector.addGoal(2, ZombieAttackGoal(zombie, 1.4, false))
        goalSelector.addGoal(7, RoamGoal(zombie, targetManager, 1.25))
        goalSelector.addGoal(8, UnstuckGoal(zombie, random))

        if (zombie is Drowned) {
            goalSelector.addGoal(2, Drowned.DrownedTridentAttackGoal(zombie, 1.0, 60, 10.0f))
        }

        spawnMobInWorld(zombie)
    }

    private fun spawnSkeleton() {
        val skeleton = createMob(skeletonTypes) ?: return

        var baseSpeed = skeleton.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)

        if (random.nextFloat() < 0.075f) {
            val scale = random.nextFloat(0.75f, 2.5f)
            EntityUtil.setAttribute(skeleton, Attributes.SCALE, scale.toDouble())
            baseSpeed *= (1.0 / scale.toDouble().pow(1.15)).coerceIn(0.6, 1.1)
        }

        EntityUtil.setAttribute(skeleton, Attributes.MOVEMENT_SPEED, baseSpeed)

        val mobAccess = skeleton as MobAccessor
        val goalSelector: GoalSelector = mobAccess.goalSelector

        GoalModifier.clear(goalSelector)
        GoalModifier.clear(mobAccess.targetSelector)

        goalSelector.addGoal(1, BreakDoorGoal(skeleton) { true })
        goalSelector.addGoal(7, RoamGoal(skeleton, targetManager, 1.25))
        goalSelector.addGoal(8, UnstuckGoal(skeleton, random))

        val stack = skeleton.getItemInHand(ProjectileUtil.getWeaponHoldingHand(skeleton, Items.BOW))

        if (stack.isOf(Items.BOW)) {
            goalSelector.addGoal(4, RangedBowAttackGoal(skeleton, 1.1, 20, 15.0f))
        } else {
            goalSelector.addGoal(4, object : MeleeAttackGoal(skeleton, 1.4, false) {
                override fun start() {
                    super.start()
                    skeleton.isAggressive = true
                }

                override fun stop() {
                    super.stop()
                    skeleton.isAggressive = false
                }
            })
        }

        spawnMobInWorld(skeleton)
    }

    private fun spawnPhantom() {
        val phantom = createMob(EntityType.PHANTOM) ?: return

        phantom.phantomSize = 0

        if (random.nextFloat() < 0.25f) {
            EntityUtil.setAttribute(phantom, Attributes.SCALE, random.nextFloat(0.2f, 5.0f).toDouble())
        }

        val mobAccess = phantom as MobAccessor
        GoalModifier.clear(mobAccess.targetSelector)

        spawnMobInWorld(phantom)
    }

    private fun spawnGhast() {
        val ghast = createMob(EntityType.GHAST) ?: return

        EntityUtil.setAttribute(ghast, Attributes.SCALE, random.nextFloat(0.2f, 1.0f).toDouble())

        spawnMobInWorld(ghast)
    }

    private fun spawnVindicator() {
        val vindicator = createMob(EntityType.VINDICATOR) ?: return

        var baseSpeed = vindicator.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)

        if (random.nextFloat() < 0.05f) {
            val scale = random.nextFloat(0.75f, 1.3f)
            EntityUtil.setAttribute(vindicator, Attributes.SCALE, scale.toDouble())
            baseSpeed *= (1.0 / scale.toDouble().pow(1.15)).coerceIn(0.75, 1.12)
        }

        EntityUtil.setAttribute(vindicator, Attributes.MOVEMENT_SPEED, baseSpeed)

        spawnMobInWorld(vindicator)
    }

    private fun spawnEvoker() {
        val evoker = createMob(EntityType.EVOKER) ?: return
        spawnMobInWorld(evoker)
    }

    private fun <T : Mob> createMob(types: WeightedList<EntityType<out T>>): T? {
        val type = types.getRandomElement(random) ?: return null
        return createMob(type)
    }

    private fun <T : Mob> createMob(type: EntityType<out T>): T? {
        val mob = type.create(
            world,
            null,
            stage.origin(),
            EntitySpawnReason.COMMAND,
            false,
            false
        ) ?: return null

        configureMob(mob)

        return mob
    }

    private fun configureMob(mob: Mob) {
        val pos: BlockPos = stage.origin()

        mob.setPersistenceRequired()
        mob.setPos(Vec3.atBottomCenterOf(pos))

        mob.getAttribute(Attributes.FOLLOW_RANGE)?.baseValue = 100.0

        val navigation = mob.navigation
        navigation.setMaxVisitedNodesMultiplier(2.5f)

        if (navigation is GroundPathNavigation) {
            navigation.setCanOpenDoors(true)
            navigation.setCanWalkOverFences(true)
        }
    }

    private fun spawnMobInWorld(mob: Mob) {
        world.addFreshEntity(mob)
        mobCount++
    }

    private enum class SpawnType {
        ZOMBIE, SKELETON, PHANTOM, GHAST, VINDICATOR, EVOKER
    }
}
