package work.lclpnet.ap2.deadline

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.scheduler.Ticks
import java.util.Random

private val WORLD_BORDER_DELAY = Ticks.minutes(3).toLong()
private val WORLD_BORDER_TIME = Ticks.minutes(1).toLong()

class DeadlineInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap, private val schema: DeadlineMapSchema) :
    EliminationGameInstance(gameHandle, level, map) {

    private val random = Random()
    private val riders = Riders(gameHandle, random)
    private val trail = LightTrail(level)
    private val powerUps = PowerUps(gameHandle, map, level, random, commons().debugController()) { riders.cycle(it) }

    override fun teleportPlayers() {
        val spawnBox = requireNotNull(schema.spawnBox) {
            "Map property \"Spawn box\" is not set in the deadline schema"
        }

        // riders start facing the arena center, so nobody spawns aimed at a nearby wall
        val border = commons().readWorldBorderConfig()
        val center = Vec3(border.centerX + 0.5, 0.0, border.centerZ + 0.5)

        teleportToRandomSpawns(spawnBox, schema.scanStarts, lookAt = center)
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        useSmoothDeath()
        disableTeleportEliminated()

        gameHandle.protect { config ->
            // riders caught outside the shrinking world border take damage until they die
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
            }
        }

        val team = createTeam()
        riders.assignColors()
        initHooks()
        riders.spawnMounts(team)
    }

    override fun go() {
        eliminateBelowCriticalHeight()
        powerUps.spawn(schema.powerUpSpawns)
        powerUps.startRefreshing(schema.powerUpSpawns)

        // the border closes in after a while, so the game is guaranteed to end
        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, 0)

        gameHandle.rootScheduler.interval(1) { -> tick() }
    }

    private fun tick() {
        trail.tick()

        for (player in gameHandle.participants) {
            val cycle = riders.cycle(player.uuid) ?: continue

            val before = cycle.sheep.position()
            cycle.tick(player.lastClientInput)
            val movement = cycle.sheep.position().subtract(before)

            // crashing into a wall or driving into a trail. phased riders pass through trails
            if (cycle.crashed || (!cycle.phased && trail.collides(player.boundingBox, movement, player.uuid))) {
                eliminate(player)
                continue
            }

            trail.extend(player.uuid, cycle.sheep.position(), riders.color(player.uuid))
            SpeedHud.show(player, cycle)
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
        val team = manager.createTeam("deadline")
        team.collisionRule = Team.CollisionRule.NEVER
        manager.joinTeam(gameHandle.participants, team)
        return team
    }

    private fun initHooks() {
        // riders cannot leave their sheep
        EntityDismountCallback.HOOK.registerWith(hooks) { entity, _ -> entity is ServerPlayer }

        // clean up the mount if a rider disconnects
        ServerPlayConnectionHooks.DISCONNECT.registerWith(hooks) { handler, _ ->
            handler.player.vehicle?.discard()
        }

        // riders caught outside the world border must not regenerate the border damage away
        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            entity is ServerPlayer && health > entity.health && !level.worldBorder.isWithinBounds(entity.boundingBox)
        }

        powerUps.initHooks()
    }

}
