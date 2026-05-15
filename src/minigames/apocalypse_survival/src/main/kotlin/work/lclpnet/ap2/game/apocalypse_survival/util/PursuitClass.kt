package work.lclpnet.ap2.game.apocalypse_survival.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import work.lclpnet.ap2.api.base.Participants
import java.util.UUID

private const val INITIAL_FORCED_TARGET_SECONDS = 10

class PursuitClass<T : Mob>(
    private val participants: Participants,
    private val capacityPerPlayer: Int
) {

    private val pursuitMap = HashMap<UUID, Pursuit<T>>(participants.count())
    private val mobs = HashSet<T>()
    private val pursuitMember = HashMap<T, Pursuit<T>>()
    private val forcedTarget = Object2IntOpenHashMap<T>()

    init {
        for (player in participants) {
            val uuid = player.uuid
            val playerGetter: () -> LivingEntity? = { participants.getParticipant(uuid).orElse(null) }
            pursuitMap[uuid] = Pursuit(playerGetter, capacityPerPlayer)
        }
    }

    fun addMob(mob: T) {
        mobs.add(mob)
        forceTargetInitially(mob)
    }

    fun removeMob(mob: T) {
        mobs.remove(mob)
        stopPursuit(mob)
    }

    fun removeParticipant(player: ServerPlayer) {
        pursuitMap.remove(player.uuid)
    }

    private fun forceTargetInitially(mob: T) {
        if (mobs.size >= participants.count() * capacityPerPlayer) return

        val player = playerWithLeastPursuers() ?: return
        val pursuit = pursuitMap[player.uuid] ?: return

        forcedTarget.put(mob, INITIAL_FORCED_TARGET_SECONDS)
        changePursuit(mob, player, pursuit)
    }

    fun update() {
        for (pursuit in pursuitMap.values) {
            pursuit.setNeedsUpdate()
        }

        nextMob@ for (mob in mobs) {
            if (forcedTarget.containsKey(mob) && shouldKeepForcedTarget(mob)) continue

            for (player in playersByDistance(mob)) {
                val pursuit = pursuitMap[player.uuid] ?: continue

                if (pursuit.hasCapacity()) {
                    changePursuit(mob, player, pursuit)
                    continue@nextMob
                }

                val mostDistant = pursuit.getMostDistantPursuer()

                if (mostDistant == null || mob.distanceToSqr(player) >= mostDistant.distanceToSqr(player)) {
                    continue
                }

                stopPursuit(mostDistant)
                changePursuit(mob, player, pursuit)
            }
        }
    }

    private fun shouldKeepForcedTarget(mob: T): Boolean {
        val remaining = forcedTarget.getInt(mob)

        if (remaining <= 1) {
            forcedTarget.removeInt(mob)
            return false
        }

        forcedTarget.put(mob, remaining - 1)
        return true
    }

    private fun playersByDistance(mob: T): Iterable<ServerPlayer> =
        Iterable { participants.stream().sorted(Comparator.comparingDouble { mob.distanceToSqr(it) }).iterator() }

    private fun changePursuit(mob: T, target: LivingEntity, pursuit: Pursuit<T>) {
        val oldPursuit = pursuitMember.put(mob, pursuit)
        oldPursuit?.removePursuer(mob)

        if (pursuit.addPursuer(mob)) {
            mob.target = target
        } else {
            stopPursuit(mob)
        }
    }

    private fun stopPursuit(mob: T) {
        pursuitMember.remove(mob)?.removePursuer(mob)
        mob.target = null
        forcedTarget.removeInt(mob)
    }

    private fun playerWithLeastPursuers(): ServerPlayer? =
        participants.stream().min(Comparator.comparingInt { player ->
            pursuitMap[player.uuid]?.getPursuerCount() ?: Int.MAX_VALUE
        }).orElse(null)
}
