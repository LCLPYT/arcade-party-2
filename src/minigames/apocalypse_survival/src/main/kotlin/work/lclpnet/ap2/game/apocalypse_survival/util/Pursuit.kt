package work.lclpnet.ap2.game.apocalypse_survival.util

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob

class Pursuit<T : Mob>(
    private val targetGetter: () -> LivingEntity?,
    private val capacity: Int
) {

    private val pursuers = HashSet<T>(capacity)
    private val byDistance = ArrayList<T>(capacity)
    private var needsUpdate = false

    fun hasCapacity() = pursuers.size < capacity

    fun addPursuer(mob: T): Boolean {
        if (!hasCapacity() || !pursuers.add(mob)) return false

        setNeedsUpdate()
        return true
    }

    fun removePursuer(mob: T) {
        if (pursuers.remove(mob)) {
            setNeedsUpdate()
        }
    }

    fun getMostDistantPursuer(): T? {
        if (needsUpdate) updateDistances()

        return byDistance.lastOrNull()
    }

    private fun updateDistances() {
        byDistance.clear()

        val target = targetGetter() ?: return

        byDistance.addAll(pursuers)
        byDistance.sortBy { target.distanceToSqr(it) }
    }

    fun setNeedsUpdate() {
        needsUpdate = true
    }

    fun getPursuerCount() = pursuers.size
}
