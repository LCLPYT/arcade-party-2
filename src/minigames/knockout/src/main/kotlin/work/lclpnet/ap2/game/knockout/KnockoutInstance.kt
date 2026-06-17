package work.lclpnet.ap2.game.knockout

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.ChatFormatting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.actor.ActorSpawnedCallback
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.ext.runEvery
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.knockout.util.ImpactDetector
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.whenBelowCriticalHeight
import work.lclpnet.ap2.impl.actor.GravityFieldActor
import work.lclpnet.ap2.impl.util.world.CombatIdleManager
import work.lclpnet.ap2.impl.util.world.DestroyStageManager
import work.lclpnet.ap2.impl.util.world.KnockbackKillTracker
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.EntityDamageCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.LocalizedFormat
import work.lclpnet.kibu.translate.text.RootText
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

private const val CHARGE_INCREMENT = 0.075
private const val CHARGE_CRITICAL_INCREMENT = 0.08
private const val CRITICAL_THRESHOLD = 2.5
private const val IMPACT_STRENGTH_THRESHOLD = 0.6
private const val MIN_IMPACT_CHARGE = 1.6
private const val IMPACT_DESTRUCTION_MULTIPLIER = 0.35
private const val IDLE_DAMAGE_MULTIPLIER = 3.0
private val IDLE_GLOW_TICKS = Ticks.seconds(15)

private val DamageDealt = Stat("damage_dealt", 0f, unit = StatUnits.Percent)
private val DamageReceived = Stat("damage_received", 0f, higherIsBetter = false, unit = StatUnits.Percent)
private val ImpactDamageDone = Stat("impact_damage_done", 0f)
private val ImpactDamageCaused = Stat("impact_damage_caused", 0f)

class KnockoutInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map) {

    private val charge = Object2DoubleOpenHashMap<UUID>()
    private val hit = Object2BooleanOpenHashMap<UUID>()
    private val blockDestruction = Object2DoubleOpenHashMap<BlockPos>()
    private val idleManager = CombatIdleManager(gameHandle.participants, IDLE_GLOW_TICKS)
    private lateinit var crumble: KnockoutWorldCrumble
    private lateinit var impactDetector: ImpactDetector
    private lateinit var destroyStageManager: DestroyStageManager
    private lateinit var killTracker: KnockbackKillTracker
    private val stats = useFFAStats(winManager, listOf(
        Kills, DistanceMoved, TimeSurvived, DamageDealt, DamageReceived, ImpactDamageDone, ImpactDamageCaused
    ))

    init {
        useOldCombat()
    }

    override fun start() {
        val movementObserver = PlayerMovementObserver(
            ChunkedCollisionDetector(),
            gameHandle.participants::isParticipating
        )
        val gravityManipulator = GravityFieldActor.Manipulator()
        val hooks = gameHandle.hooks

        movementObserver.init(hooks, gameHandle.server)

        ActorSpawnedCallback.HOOK.registerWith(hooks) { actor ->
            if (actor is GravityFieldActor) {
                actor.enable(movementObserver, gravityManipulator, gameHandle.hooks)
            }
        }

        super.start()
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        trackSurvivalTime(stats)
        trackDistanceMoved(stats)

        whenBelowCriticalHeight { player ->
            val killer = killTracker.getLastAttacker(player) as? ServerPlayer

            if (killer != null && killer != player) {
                gainKill(killer, stats)
            }

            eliminate(player, killTracker.killMessage(player, gameHandle.deathMessages))
        }

        crumble = KnockoutWorldCrumble(level, map)
        crumble.init()
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config, ::canDamage)
        }

        val hooks = gameHandle.hooks
        val scheduler = gameHandle.scheduler
        val participants = gameHandle.participants

        EntityDamageCallback.HOOK.registerWith(hooks) { entity, source, damage ->
            if (entity is ServerPlayer && source.entity is ServerPlayer && entity.hurtTime <= 0) {
                onDamage(entity, source.entity as ServerPlayer, damage)
                return@registerWith true
            }
            false
        }

        runAfter(crumble.delaySeconds.seconds) {
            beginCrumble()
        }

        val debugController = commons().debugController()
        impactDetector = ImpactDetector(participants, debugController, 0.1)
        destroyStageManager = DestroyStageManager(level)
        killTracker = KnockbackKillTracker(participants)

        impactDetector.enable(scheduler)
        killTracker.init(scheduler)
        impactDetector.onImpact().register { player, collisions -> onImpact(player, collisions) }
        impactDetector.onMiss().register { player -> onMiss(player) }

        runEvery(2.seconds) {
            sendChargeDisplay()
        }

        idleManager.onEnterIdle().register { player ->
            gameHandle.translations
                .translateText("idle")
                .formatted(YELLOW)
                .sendTo(player)

            player.level().sendParticles(ParticleTypes.WITCH, player.x, player.y, player.z, 50, 0.5, 1.0, 0.5, 0.1)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8f, 1f)
            player.addEffect(MobEffectInstance(MobEffects.GLOWING, Int.MAX_VALUE, 1, false, false, true))
        }

        idleManager.onLeaveIdle().register { player -> player.removeEffect(MobEffects.GLOWING) }

        idleManager.enable(scheduler, hooks)
    }

    override fun participantRemoved(player: ServerPlayer) {
        data.add(player, chargeDetail(player))

        if (gameHandle.participants.count() == 1) {
            val winner = gameHandle.participants.iterator().next()
            data.add(winner, chargeDetail(winner))
        }

        super.participantRemoved(player)
    }

    private fun chargeDetail(player: ServerPlayer): TranslatedText {
        return TranslatedText.create(
            { lang -> RootText.create().append(formattedCharge(chargeOf(player)).translateTo(lang)) },
            gameHandle.translations::getLanguage
        )
    }

    private fun formattedCharge(charge: Double): LocalizedFormat {
        return LocalizedFormat.format("%d%%", (charge * 100).roundToInt())
    }

    private fun sendChargeDisplay() {
        for (player in gameHandle.participants) {
            sendCharge(player)
        }
    }

    private fun beginCrumble() {
        crumble.start(gameHandle.scheduler)
    }

    private fun canDamage(entity: Entity, source: DamageSource): Boolean {
        val participants = gameHandle.participants

        return source.isOf(DamageTypes.PLAYER_ATTACK) && entity is ServerPlayer
                && !winManager.gameOver
                && source.entity is ServerPlayer
                && participants.isParticipating(entity)
                && participants.isParticipating(source.entity as ServerPlayer)
    }

    private fun onDamage(player: ServerPlayer, attacker: ServerPlayer, damage: Float) {
        var increment = if (damage > 2.0f) CHARGE_CRITICAL_INCREMENT else CHARGE_INCREMENT

        if (idleManager.isOutOfCombat(player)) {
            increment *= IDLE_DAMAGE_MULTIPLIER
            idleManager.resetCombat(player)
        }

        val finalIncrement = increment
        val power = charge.getOrDefault(player.uuid, 0.0) + finalIncrement
        charge.put(player.uuid, power)

        synchronized(this) {
            hit.put(player.uuid, true)
        }

        killTracker.onHit(player, attacker)

        stats.modify(attacker, DamageDealt) { it + finalIncrement.toFloat() }
        stats.modify(player, DamageReceived) { it + finalIncrement.toFloat() }

        var vec = player.position().subtract(attacker.position()).normalize()
        vec = Vec3(vec.x, 0.1, vec.z)
        vec = vec.scale(power)

        VelocityModifier.setVelocity(player, vec)

        val world: ServerLevel = player.level()
        val x = player.x
        val y = player.y
        val z = player.z

        world.sendParticles(ParticleTypes.CLOUD, x, y, z, 25, 0.25, 0.25, 0.25, 0.1)
        world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5f, 1.2f)
        world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5f, 1.25f)

        if (power > CRITICAL_THRESHOLD) {
            world.playSound(null, x, y, z, SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.25f, 1.25f)
            world.sendParticles(ParticleTypes.RAID_OMEN, x, y + 1, z, 10, 0.5, 0.5, 0.5, 0.1)
        }

        sendCharge(player)

        impactDetector.checkImpact(player, vec)
    }

    private fun sendCharge(player: ServerPlayer) {
        val charge = chargeOf(player)

        player.sendOverlayMessage(formattedCharge(charge)
            .translateTo(gameHandle.translations.getLanguage(player))
            .copy().withStyle(if (charge > CRITICAL_THRESHOLD) DARK_RED else WHITE))
    }

    private fun onImpact(player: ServerPlayer, collisions: Iterable<BlockPos>) {
        val wasHit = synchronized(this) { hit.put(player.uuid, false) }
        if (!wasHit) return

        val velocity = impactDetector.getVelocity(player) ?: return

        val charge = chargeOf(player)
        if (charge < MIN_IMPACT_CHARGE) return

        val strength = velocity.length()
        if (strength < IMPACT_STRENGTH_THRESHOLD) return

        val damage = sqrt(strength - IMPACT_STRENGTH_THRESHOLD) * IMPACT_DESTRUCTION_MULTIPLIER
        val world = level

        stats.modify(player, ImpactDamageCaused) { it + damage.toFloat() }

        killTracker.getLastAttacker(player)?.let { it as? ServerPlayer }?.let { attacker ->
            if (attacker != player) {
                stats.modify(attacker, ImpactDamageDone) { it + damage.toFloat() }
            }
        }

        var anyBroke = false

        for (mutable in collisions) {
            val pos = mutable.immutable()
            val destruction = blockDestruction.getOrDefault(pos, 0.0) + damage
            blockDestruction.put(pos, destruction)

            if (destruction < 1.0) {
                destroyStageManager.setDestroyStage(pos, (destruction * 10).toInt())
                continue
            }

            destroyStageManager.removeDestroyStage(pos)
            world.destroyBlock(pos, false)
            anyBroke = true
        }

        if (anyBroke) {
            world.playSound(null, player.x, player.y, player.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 0.2f, 1.15f)
        } else {
            world.playSound(null, player.x, player.y, player.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.18f, 0.85f)
        }
    }

    private fun chargeOf(player: ServerPlayer): Double = charge.getOrDefault(player.uuid, 0.0)

    @Synchronized
    private fun onMiss(player: ServerPlayer) {
        hit.put(player.uuid, false)
    }
}
