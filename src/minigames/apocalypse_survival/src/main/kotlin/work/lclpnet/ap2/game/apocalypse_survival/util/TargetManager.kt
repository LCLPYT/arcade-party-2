package work.lclpnet.ap2.game.apocalypse_survival.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.game.map.GameMap
import java.util.*

class TargetManager(participants: Participants, map: GameMap, random: Random) {

    private val zombiePursuit = PursuitClass<Zombie>(participants, 20)
    private val skeletonPursuit = PursuitClass<AbstractSkeleton>(participants, 10)
    private val phantomPursuit = PursuitClass<Phantom>(participants, 3)
    private val vindicatorPursuit = PursuitClass<Vindicator>(participants, 5)
    private val pursuits = arrayOf(zombiePursuit, skeletonPursuit, phantomPursuit, vindicatorPursuit)
    val densityManager = MobDensityManager(map, random)

    fun addZombie(zombie: Zombie) {
        densityManager.startTracking(zombie)
        zombiePursuit.addMob(zombie)
    }

    fun addSkeleton(skeleton: AbstractSkeleton) {
        densityManager.startTracking(skeleton)
        skeletonPursuit.addMob(skeleton)
    }

    fun addPhantom(phantom: Phantom) {
        densityManager.startTracking(phantom)
        phantomPursuit.addMob(phantom)
    }

    fun addVindicator(vindicator: Vindicator) {
        densityManager.startTracking(vindicator)
        vindicatorPursuit.addMob(vindicator)
    }

    fun removeMob(mob: Mob) {
        densityManager.stopTracking(mob)

        when (mob) {
            is Zombie -> zombiePursuit.removeMob(mob)
            is Skeleton -> skeletonPursuit.removeMob(mob)
            is Phantom -> phantomPursuit.removeMob(mob)
            is Vindicator -> vindicatorPursuit.removeMob(mob)
        }
    }

    fun removeParticipant(player: ServerPlayer) {
        for (pursuit in pursuits) {
            pursuit.removeParticipant(player)
        }
    }

    fun update() {
        densityManager.update()

        for (pursuit in pursuits) {
            pursuit.update()
        }
    }
}
