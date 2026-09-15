package work.lclpnet.ap2.game.player

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*

class PlayerManagerImpl(private val server: MinecraftServer) : PlayerManager {

    private val lock = Any()
    private val participants = HashSet<UUID>()
    private val permanentSpectators = HashSet<UUID>()
    private val initial = HashSet<PlayerRef>()
    private var prepare = false
    private var finale = false
    private var listener: ParticipantListener? = null

    override val asSet: Set<ServerPlayer> get() = synchronized(lock) {
        PlayerLookup.all(server)
            .filter { it.uuid in participants }
            .toSet()
    }

    override fun isParticipating(uuid: UUID): Boolean = synchronized(lock) {
        uuid in participants
    }

    override fun offer(player: ServerPlayer): Boolean = synchronized(lock) {
        if (!prepare || finale) return@synchronized false

        participants.add(player.uuid)
        true
    }

    override fun startPreparation(): Unit = synchronized(lock) {
        prepare = true
        reset()
    }

    override fun startMiniGame(): Unit = synchronized(lock) {
        prepare = false
        reset()
        setInitial()
    }

    private fun setInitial() {
        for (uuid in participants) {
            val player = server.playerList.getPlayer(uuid)
            initial.add(if (player != null) PlayerRef.create(player) else PlayerRef.createForUuid(uuid))
        }
    }

    private fun reset() {
        initial.clear()

        if (finale) {
            // remove finalists who left
            participants.removeAll { server.playerList.getPlayer(it) == null }
        } else {
            addAllPlayers()
        }
    }

    private fun addAllPlayers() {
        // clear old entries in case the player has left
        participants.clear()

        PlayerLookup.all(server)
            .map { it.uuid }
            .filter { it !in permanentSpectators }
            .forEach { participants.add(it) }
    }

    override fun enterFinale(finalists: Set<ServerPlayer>): Unit = synchronized(lock) {
        finale = true
        participants.clear()

        finalists.forEach { participants.add(it.uuid) }
    }

    override fun isPermanentSpectator(player: ServerPlayer): Boolean = synchronized(lock) {
        player.uuid in permanentSpectators
    }

    override fun addPermanentSpectator(player: ServerPlayer): Unit = synchronized(lock) {
        permanentSpectators.add(player.uuid)

        if (prepare && !finale && participants.remove(player.uuid)) {
            listener?.participantRemoved(player)
        }
    }

    override fun removePermanentSpectator(player: ServerPlayer): Unit = synchronized(lock) {
        permanentSpectators.remove(player.uuid)

        if (prepare && !finale) {
            participants.add(player.uuid)
        }
    }

    override fun remove(player: ServerPlayer): Unit = synchronized(lock) {
        if (participants.remove(player.uuid)) {
            listener?.participantRemoved(player)
        }
    }

    override fun bind(listener: ParticipantListener?): Unit = synchronized(lock) {
        this.listener = listener
    }

    override fun getParticipant(uuid: UUID): ServerPlayer? = synchronized(lock) {
        if (uuid !in participants) {
            null
        } else {
            server.playerList.getPlayer(uuid)
        }
    }

    override fun leaveFinale(): Unit = synchronized(lock) {
        finale = false
        addAllPlayers()
    }

    override val isFinale: Boolean get() = synchronized(lock) {
        finale
    }

    override val initialParticipants: Set<PlayerRef> get() = synchronized(lock) {
        initial.toSet()
    }
}