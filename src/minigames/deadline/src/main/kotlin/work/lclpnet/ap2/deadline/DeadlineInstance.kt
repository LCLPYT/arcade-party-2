package work.lclpnet.ap2.deadline

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.DyeColor
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import java.util.Random
import java.util.UUID
import kotlin.math.roundToInt

class DeadlineInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) :
    EliminationGameInstance(gameHandle, level, map) {

    private val random = Random()
    private val colors = HashMap<UUID, DyeColor>()
    private val cycles = HashMap<UUID, LightCycle>()
    private val trail = LightTrail(level)

    override fun prepare() {
        useRemainingPlayersDisplay()

        val team = createTeam()
        assignColors()
        initHooks()
        spawnMounts(team)
    }

    override fun go() {
        eliminateBelowCriticalHeight()

        runEveryTick { tick() }
    }

    private fun tick() {
        for (player in gameHandle.participants) {
            val cycle = cycles[player.uuid] ?: continue
            cycle.tick(player.lastClientInput)
            trail.extend(player.uuid, cycle.sheep.position(), colors.getValue(player.uuid))
            showSpeed(player, cycle)

            // driving into any trail
            if (trail.collides(player.boundingBox, player.uuid)) eliminate(player)
        }
    }

    // show the rider's speed on the xp bar in km/h
    private fun showSpeed(rider: ServerPlayer, cycle: LightCycle) {
        val kmh = (cycle.speed * 3.6f).roundToInt()
        rider.connection.send(ClientboundSetExperiencePacket(cycle.speedFraction, 0, kmh))
    }

    // reset the xp bar readout when a rider stops riding
    private fun clearSpeed(rider: ServerPlayer) {
        rider.connection.send(ClientboundSetExperiencePacket(0f, 0, 0))
    }

    override fun participantRemoved(player: ServerPlayer) {
        player.vehicle?.discard()
        cycles.remove(player.uuid)
        trail.discard(player.uuid)
        clearSpeed(player)
        super.participantRemoved(player)
    }

    private fun assignColors() {
        val palette = ColorUtil.VIVID_DYE_COLORS.shuffled(random)
        var i = 0
        for (player in gameHandle.participants) {
            colors[player.uuid] = palette[i++ % palette.size]
        }
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
    }

    private fun spawnMounts(team: PlayerTeam) {
        // players were already teleported to their spawns by the base start sequence
        for (player in gameHandle.participants) {
            val sheep = spawnSheep(player, colors.getValue(player.uuid))
            cycles[player.uuid] = LightCycle(sheep)
            gameHandle.scoreboardManager.joinTeam(sheep, team)
        }
    }

    private fun spawnSheep(player: ServerPlayer, color: DyeColor): Sheep {
        val world = player.level()
        val sheep = Sheep(EntityTypes.SHEEP, world)

        sheep.setNoAi(true)
        sheep.isInvulnerable = true
        sheep.isSilent = true
        sheep.setColor(color)
        sheep.setYRot(player.yRot)
        sheep.setYBodyRot(player.yRot)
        sheep.setPosRaw(player.x, player.y, player.z)

        world.addFreshEntity(sheep)
        player.startRiding(sheep, true, false)

        return sheep
    }
}
