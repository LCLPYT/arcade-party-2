package work.lclpnet.ap2.impl.util.world

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.DeathMessages
import work.lclpnet.kibu.hook.util.OnGroundDetector
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class KnockbackKillTracker(
    private val participants: Participants,
    private val trackerTimeout: Duration = 10.seconds,
) {

    private val entries = HashMap<UUID, Entry>()

    fun init(scheduler: TaskScheduler) {
        scheduler.interval(1) { -> tick() }
    }

    fun onHit(victim: ServerPlayer, attacker: Entity) {
        if (victim.uuid == attacker.uuid) return
        entries[victim.uuid] = Entry(attacker.uuid, trackerTimeout.inWholeTicks.toInt())
    }

    fun getLastAttacker(victim: ServerPlayer): Entity? {
        val entry = entries[victim.uuid] ?: return null

        val player = victim.level().server.playerList.getPlayer(entry.attacker)

        if (player != null) return player

        return victim.level().getEntity(entry.attacker)
    }

    fun killMessage(victim: ServerPlayer, deathMessages: DeathMessages): TranslatedText? {
        val killer = getLastAttacker(victim) as? ServerPlayer ?: return null

        return deathMessages.killedBy(victim, killer)
    }

    private fun tick() {
        val it = entries.entries.iterator()

        while (it.hasNext()) {
            val (uuid, entry) = it.next()
            val player = participants.getParticipant(uuid)

            if (player == null) {
                it.remove()
                continue
            }

            if (!OnGroundDetector.isOnGroundServer(player)) {
                entry.airborne = true
                continue
            }

            if (entry.airborne || --entry.timeout <= 0) {
                it.remove()
            }
        }
    }

    private class Entry(val attacker: UUID, var timeout: Int) {
        var airborne = false
    }
}
