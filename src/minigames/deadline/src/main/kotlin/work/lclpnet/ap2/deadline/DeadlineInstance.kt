package work.lclpnet.ap2.deadline

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.border.BorderStatus
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.core.hook.WorldBorderPhaseCallback
import work.lclpnet.ap2.deadline.item.DeadlinePowerUps
import work.lclpnet.ap2.deadline.rider.Riders
import work.lclpnet.ap2.deadline.trail.LightTrail
import work.lclpnet.ap2.deadline.trail.TrailSpec
import work.lclpnet.ap2.deadline.vehicle.BikeSpec
import work.lclpnet.ap2.deadline.vehicle.SpeedHud
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import java.util.Random
import kotlin.time.Duration.Companion.minutes

private val PowerUpsUsed = Stat("power_ups_used", 0)

private val WORLD_BORDER_DELAY = 3.minutes
private val WORLD_BORDER_TIME = 1.minutes

class DeadlineInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val schema: DeadlineMapSchema,
) : EliminationGameInstance(gameHandle, level, map) {

    private val random = Random()
    private val riders = Riders(gameHandle, random, BikeSpec.fromMap(map))
    private val trail = LightTrail(level, TrailSpec.fromMap(map))
    private val stats = useFFAStats(winManager, listOf(
        TimeSurvived, Kills, PowerUpsUsed, DistanceMoved
    ))
    private val powerUps = DeadlinePowerUps(
        gameHandle,
        map,
        level,
        random,
        commons().debugController(),
        riders,
        schema.powerUpSpawns,
    ) {
        stats.increment(it, PowerUpsUsed)
    }

    override fun teleportPlayers() {
        val spawnBox = schema.spawnBox!!

        // riders start facing the arena center, so nobody spawns aimed at a nearby wall
        val border = commons().readWorldBorderConfig()
        val center = Vec3(border.centerX + 0.5, 0.0, border.centerZ + 0.5)

        teleportToRandomSpawns(spawnBox, schema.scanStarts, lookAt = center)
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        useSmoothDeath()
        trackSurvivalTime(stats)
        disableTeleportEliminated()

        val team = createTeam()
        riders.assignColors()
        initHooks()
        riders.spawnMounts(team)
    }

    override fun go() {
        gameHandle.protect { config ->
            // riders caught outside the shrinking world border take damage until they die
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
            }
        }

        eliminateBelowCriticalHeight()
        powerUps.spawn()
        powerUps.startRefreshing()

        // the border closes in after a while, so the game is guaranteed to end
        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY.inWholeTicks, WORLD_BORDER_TIME.inWholeTicks, 0)

        gameHandle.rootScheduler.interval(1) { -> tick() }
    }

    private fun tick() {
        trail.tick()

        val crashed = mutableListOf<ServerPlayer>()

        for (player in gameHandle.participants) {
            val cycle = riders.cycle(player.uuid) ?: continue

            val before = cycle.sheep.position()
            cycle.tick(player.lastClientInput)
            val movement = cycle.sheep.position().subtract(before)

            // crashing into a wall or driving into a trail. phased riders pass through trails
            val trailOwner = if (cycle.phased) null else trail.hit(player.boundingBox, movement, player.uuid)

            if (cycle.crashed || trailOwner != null) {
                // driving into another rider's trail counts as a kill for its owner
                if (trailOwner != null && trailOwner != player.uuid) {
                    gameHandle.participants.getParticipant(trailOwner)?.let { gainKill(it, stats) }
                }

                crashed.add(player)
                continue
            }

            stats.modify(player, DistanceMoved) { it + movement.horizontalDistance() }
            trail.extend(player.uuid, cycle.sheep.position(), riders.color(player.uuid))
            SpeedHud.show(player, cycle)
        }

        // riders ramming into each other take each other down, so both earn the kill
        val dying = crashed.toSet()

        for ((first, second) in riders.collidingPairs()) {
            if (first in dying || second in dying) continue

            gainKill(first, stats)
            gainKill(second, stats)

            if (first !in crashed) crashed.add(first)
            if (second !in crashed) crashed.add(second)
        }

        // riders that crash in the same tick share their rank
        if (crashed.isNotEmpty()) {
            eliminateAll(crashed)
        }
    }

    // intentionally not calling super.onDeath -> no equipment/experience drops
    override fun onDeath(player: ServerPlayer, attacker: Entity?) {}

    override fun onEliminated(player: ServerPlayer) {
        super.onEliminated(player)

        SoundHelper.playSoundAt(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.LAVA, 100, 0.5, 0.5, 0.5, 0.2)
    }

    override fun participantRemoved(player: ServerPlayer) {
        riders.remove(player)
        trail.discard(player.uuid)
        SpeedHud.clear(player)
        super.participantRemoved(player)
    }

    private fun createTeam(): PlayerTeam {
        val manager = gameHandle.scoreboardManager
        val team = manager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        manager.joinTeam(gameHandle.participants, team)
        return team
    }

    private fun initHooks() {
        // riders cannot leave their sheep
        EntityDismountCallback.HOOK.registerWith(hooks) { entity, _ -> entity is ServerPlayer }

        // riders caught outside the world border must not regenerate the border damage away
        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            entity is ServerPlayer && health > entity.health && !level.worldBorder.isWithinBounds(entity.boundingBox)
        }

        // the moving world border has no collision for the sheep: riders phase through it and take
        // the border damage outside instead. once it stands still, it acts as a regular wall
        WorldBorderPhaseCallback.HOOK.registerWith(hooks) { border, entity ->
            border.status != BorderStatus.STATIONARY && riders.isMount(entity)
        }

        powerUps.initHooks()
    }
}
