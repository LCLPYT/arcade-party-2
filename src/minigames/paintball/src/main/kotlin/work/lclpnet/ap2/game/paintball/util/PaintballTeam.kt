package work.lclpnet.ap2.game.paintball.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.json.JSONObject
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamKeyable
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.gaco.core.api.Partial
import work.lclpnet.gaco.ds.BlockBox

data class PaintballTeam(
    val spawn: Vec3,
    val yaw: Float,
    val baseBounds: BlockBox,
    val templateColor: DyeTeamKey,
    override val key: DyeTeamKey
) : TeamKeyable {

    fun participants(teamManager: TeamManager, participants: Participants): Set<ServerPlayer> =
        teamManager.getTeam(this)
            ?.getParticipatingPlayers(participants)
            ?: emptySet()
}

fun paintballTeamFromJson(json: JSONObject): Partial<PaintballTeam, DyeTeamKey> {
    val spawn = MapUtil.readCenteredVec3d(json.getJSONArray("spawn"))
    val yaw = MapUtil.readAngle(json.optFloat("yaw", 0f))
    val baseBounds = MapUtil.readBox(json.getJSONArray("base-bounds"))
    val teamId = json.getString("template-color")
    val templateColor = DyeTeamKey.byId(teamId)
        ?: throw NoSuchElementException("Unknown team template-color \"$teamId\"")

    return Partial { key ->
        PaintballTeam(spawn, yaw, baseBounds, templateColor, key)
    }
}
