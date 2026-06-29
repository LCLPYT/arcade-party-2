package work.lclpnet.ap2.game.player

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import java.util.stream.Stream

interface Participants : Iterable<ServerPlayer> {
    /**
     * @return The currently participating players.
     */
    val asSet: Set<ServerPlayer>

    val initialParticipants: Set<PlayerRef>

    fun remove(player: ServerPlayer)

    fun isParticipating(uuid: UUID): Boolean

    override fun iterator(): Iterator<ServerPlayer> = asSet.iterator()

    fun isParticipating(player: ServerPlayer): Boolean =
        isParticipating(player.getUUID())

    fun count(): Int = asSet.size

    fun getRandomParticipant(random: Random): ServerPlayer? {
        val count = count()

        if (count <= 0) {
            return null
        }

        return asSet.randomOrNull()
    }

    fun getParticipant(uuid: UUID): ServerPlayer? =
        firstOrNull { it.uuid == uuid }

    fun stream(): Stream<ServerPlayer> =
        asSet.stream()
}
