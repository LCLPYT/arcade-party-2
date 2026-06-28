package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.PlayerTeam
import java.util.*

interface TeamManager {

    val teams: Set<Team>

    fun getMinecraftTeam(key: TeamKey): PlayerTeam?

    fun getTeam(key: TeamKey): Team?

    fun getTeam(uuid: UUID): Team?

    fun partitionIntoTeams(players: Iterable<ServerPlayer>, teams: Iterable<TeamKey>)

    fun isParticipating(key: TeamKey): Boolean

    fun setTeamEliminated(team: Team)

    fun bind(listener: TeamEliminatedListener?)

    /**
     * Whether to use team color codes.
     * This enables colored player name tags above the player model.
     * However, only a limited number of colors codes exist.
     * Set this to true, if all your team rgb colors have matching color codes (Formatting).
     */
    fun setUseColorCodes(useColorCodes: Boolean)

    fun registerTeam(key: TeamKey): Team

    fun joinTeam(player: ServerPlayer, team: Team)

    val minecraftTeams: Set<PlayerTeam>
        get() = teams
            .map { it.key }
            .mapNotNull { getMinecraftTeam(it) }
            .toSet()

    val participatingTeams: Set<Team>
        get() = teams
            .filter { isParticipating(it) }
            .toSet()

    fun getTeam(player: ServerPlayer): Team? =
        getTeam(player.getUUID())

    fun isParticipating(team: Team): Boolean =
        isParticipating(team.key)

    fun isParticipating(player: ServerPlayer): Boolean {
        val team = getTeam(player) ?: return false

        return isParticipating(team)
    }

    fun isTeamMember(player: ServerPlayer, team: Team): Boolean {
        val playerTeam = getTeam(player)

        if (playerTeam != null) {
            return playerTeam == team
        }

        return false
    }

    fun areTeamMates(first: ServerPlayer, second: ServerPlayer): Boolean {
        val team = getTeam(first) ?: return false

        return isTeamMember(second, team)
    }

    fun getTeam(keyable: TeamKeyable): Team? =
        getTeam(keyable.key)
}