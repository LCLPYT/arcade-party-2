package work.lclpnet.ap2.game.player

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
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

    fun getRandomParticipant(random: Random): Optional<ServerPlayer> {
        val count = count()

        if (count <= 0) {
            return Optional.empty<ServerPlayer>()
        }

        return stream().skip(random.nextInt(count).toLong()).findFirst()
    }

    fun getParticipant(uuid: UUID): Optional<ServerPlayer> {
        return stream()
            .filter { player -> player.getUUID() == uuid }
            .findAny()
    }

    fun stream(): Stream<ServerPlayer> {
        return asSet.stream()
    }
}
