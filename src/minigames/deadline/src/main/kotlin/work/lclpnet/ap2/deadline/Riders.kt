package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.DyeColor
import net.minecraft.world.scores.PlayerTeam
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.vehicle.BikeSpec
import work.lclpnet.ap2.impl.util.ColorUtil
import java.util.Random
import java.util.UUID

/**
 * Owns the riders of the game: their assigned colors, their sheep mounts and their light cycles.
 */
class Riders(private val gameHandle: MiniGameHandle, private val random: Random, private val spec: BikeSpec) {

    private val colors = HashMap<UUID, DyeColor>()
    private val cycles = HashMap<UUID, LightCycle>()

    fun cycle(uuid: UUID): LightCycle? = cycles[uuid]

    fun color(uuid: UUID): DyeColor = colors.getValue(uuid)

    fun assignColors() {
        colors.putAll(gameHandle.colorPreferences.assign(gameHandle.participants, random, ColorUtil.VIVID_DYE_COLORS.toSet()))
    }

    fun spawnMounts(team: PlayerTeam) {
        // players were already teleported to their spawns by the base start sequence
        for (player in gameHandle.participants) {
            val color = colors.getValue(player.uuid)
            val sheep = spawnSheep(player, color)
            cycles[player.uuid] = LightCycle(sheep, spec)
            gameHandle.scoreboardManager.joinTeam(sheep, team)
            RiderOutfit.equip(player, color)
        }
    }

    fun remove(player: ServerPlayer) {
        cycles.remove(player.uuid)?.sheep?.discard()
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
        sheep.setYHeadRot(player.yRot)
        sheep.setPosRaw(player.x, player.y, player.z)

        world.addFreshEntity(sheep)
        player.startRiding(sheep, true, false)

        return sheep
    }
}
