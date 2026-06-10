package work.lclpnet.ap2.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import java.util.*

class PvpBehavior(
    val gameHandle: MiniGameHandle,
    val world: ServerLevel,
) {
    val playerMade = mutableSetOf<BlockPos>()
    val disallowUuids = mutableSetOf<UUID>()

    fun configure() {
        gameHandle.protect { config ->
            fun allowed(entity: Entity) =
                entity.uuid !in disallowUuids

            // only for allowed players
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, _ ->
                allowed(entity)
            }

            ProtectionTypes.USE_ITEM_ON_BLOCK.allow(config) { player, _ ->
                allowed(player)
            }

            listOf(
                ProtectionTypes.CONSUME_FOOD,
                ProtectionTypes.CRAFT_ITEM,
            ).forEach {
                it.allow(config) { player, _ ->
                    allowed(player)
                }
            }

            listOf(
                ProtectionTypes.USE_BLOCK,
                ProtectionTypes.CHARGE_RESPAWN_ANCHOR,
                ProtectionTypes.EAT_CAKE,
                ProtectionTypes.PICKUP_FLUID,
                ProtectionTypes.PLACE_BLOCKS,
                ProtectionTypes.BREAK_BLOCKS,
                ProtectionTypes.PLACE_FLUID,
                ProtectionTypes.PRIME_TNT,
            ).forEach {
                it.allow(config) { entity, _ ->
                    allowed(entity)
                }
            }

            ProtectionTypes.DROP_ITEM.allow(config) { player, _, _ ->
                allowed(player)
            }

            ProtectionTypes.HUNGER.allow(config) { player ->
                allowed(player)
            }

            ProtectionTypes.PICKUP_ITEM.allow(config) { player, _ ->
                allowed(player)
            }

            ProtectionTypes.PICKUP_PROJECTILE.allow(config) { player, _ ->
                allowed(player)
            }

            // always allowed
            ProtectionTypes.MODIFY_INVENTORY.allow(config)
            ProtectionTypes.SWAP_HAND_ITEMS.allow(config)

            // generic
            ProtectionTypes.EXPLOSION.allow(config)
            ProtectionTypes.ITEM_SCATTER.allow(config)
        }

        // track player made blocks
        BlockModificationHooks.BLOCK_PLACED.registerWith(gameHandle.hooks) { level, pos, entity ->
            if (level == world && entity is ServerPlayer && gameHandle.participants.isParticipating(entity)) {
                playerMade.add(pos.immutable())
            }
        }

        // only allow breaking player made blocks
        BlockModificationHooks.BREAK_BLOCK.registerWith(gameHandle.hooks) { level, pos, entity ->
            level != world || entity !is ServerPlayer || !gameHandle.participants.isParticipating(entity) || !playerMade.contains(pos)
        }
    }

    fun disallow(entity: Entity) {
        disallowUuids += entity.uuid
    }

    fun allow(entity: Entity) {
        disallowUuids -= entity.uuid
    }

    fun allowAll() {
        disallowUuids.clear()
    }
}