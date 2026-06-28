package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.Participants

interface Team : TeamKeyable {
    /**
     * Get all online players in this team.
     * Those players may or may not be participating.
     * @return A set of players in this team. Modifications on the set do not affect the actual team members.
     */
    val players: Set<ServerPlayer>

    fun addPlayer(player: ServerPlayer)

    fun removePlayer(player: ServerPlayer)

    /**
     * Get the total amount of players in this team.
     * This is not necessarily equal to `getPlayers().size()`, as offline players are also counted.
     * @return The total amount of players, online or offline, in this team.
     */
    val playerCount: Int

    /**
     * Get all participating players in this team.
     * @param participants The [Participants] manager.
     * @return A set of participants of this team. Modifications on the set do not affect the actual team participants.
     */
    fun getParticipatingPlayers(participants: Participants): Set<ServerPlayer> {
        return players
            .filter { participants.isParticipating(it) }
            .toSet()
    }
}
