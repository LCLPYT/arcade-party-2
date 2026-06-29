package work.lclpnet.ap2.game.util

import com.google.common.collect.Iterables
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.GameType
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.DeathMessages
import java.util.*
import java.util.stream.Stream

class PseudoElimination(
    private val participants: Participants,
    private val deathMessages: DeathMessages,
    private val world: ServerLevel,
) {
    private val toEliminate = HashSet<UUID>()

    constructor(handle: MiniGameHandle, world: ServerLevel) : this(handle.participants, handle.deathMessages, world)

    @Synchronized
    fun isEliminated(player: ServerPlayer): Boolean =
        toEliminate.contains(player.getUUID())

    fun isParticipating(player: ServerPlayer): Boolean =
        participants.isParticipating(player) && !isEliminated(player)

    @Synchronized
    fun commit() {
        stream().forEach { player -> participants.remove(player) }

        toEliminate.clear()
    }

    @Synchronized
    fun eliminate(player: ServerPlayer): Boolean {
        if (isEliminated(player) || !participants.isParticipating(player)) return false

        val x = player.x
        val y = player.y
        val z = player.z

        world.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f)
        world.sendParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2)

        deathMessages.getDeathMessage(player, null)
            .sendTo(PlayerLookup.all(world.server))

        player.setGameMode(GameType.SPECTATOR)

        toEliminate.add(player.getUUID())

        return true
    }

    fun stream(): Stream<ServerPlayer> = toEliminate.stream()
        .map { uuid -> participants.getParticipant(uuid) }
        .flatMap { obj -> obj.stream() }

    @Synchronized
    fun size(): Int =
        toEliminate.size

    fun streamParticipants(): Stream<ServerPlayer> =
        participants.stream().filter { player -> !isEliminated(player) }

    fun iterateParticipants(): Iterable<ServerPlayer> =
        Iterables.filter(participants, { player: ServerPlayer -> !isEliminated(player) })
}