package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import kotlin.time.Duration.Companion.seconds

/**
 * Hit a mob at the greatest distance with an arrow.
 * Every player receives a bow with arrows.
 */
object BowDistanceTask : Task {

    override val id = "bow_distance"

    override fun begin(env: TaskEnv) {
        val data = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "ap2.score.distance")
        val shotOrigin = HashMap<Int, Vec3>()

        for (player in env.players) {
            data.setScore(player, 0.0)
            giveBow(env, player)
        }

        ProjectileShootCallback.HOOK.registerWith(env.hooks) { shooter, projectile ->
            if (shooter is ServerPlayer && env.players.isParticipating(shooter) && projectile is AbstractArrow) {
                shotOrigin[projectile.id] = projectile.position()
            }
        }

        ProjectileHitEntityCallback.HOOK.registerWith(env.hooks) { projectile, hit ->
            if (projectile !is AbstractArrow) return@registerWith

            val shooter = projectile.owner as? ServerPlayer ?: return@registerWith

            if (!env.players.isParticipating(shooter)) return@registerWith

            val victim = hit.entity

            if (victim !is LivingEntity || victim is ServerPlayer) return@registerWith

            val origin = shotOrigin[projectile.id] ?: return@registerWith
            val dist = origin.distanceTo(victim.position())

            if (dist > data.getScore(shooter)) {
                data.setScore(shooter, dist)
                env.feedback(shooter, "task.feedback.bow_distance", dist.toInt(), sound = true)
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }

    private fun giveBow(env: TaskEnv, player: ServerPlayer) {
        val infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, env.level.registryAccess())
        val bow = unbreakable(ItemStack(Items.BOW))
        bow.enchant(infinity, 1)

        val inventory = player.inventory
        val slot = (0..8).firstOrNull { inventory.getItem(it).isEmpty } ?: 0

        val displaced = inventory.getItem(slot)

        if (!displaced.isEmpty) {
            env.give(player, displaced.copy())
        }

        inventory.setItem(slot, bow)
        PlayerInventoryAccess.setSelectedSlot(player, slot)

        env.give(player, ItemStack(Items.ARROW, 8))
    }
}
