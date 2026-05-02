package work.lclpnet.ap2.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.context.UseOnContext
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.ext.configureProtection
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.lobby.game.api.prot.scope.*
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import java.util.*

class PvpBehavior(
    val gameHandle: MiniGameHandle,
    val world: ServerLevel,
) {
    val playerMade = mutableSetOf<BlockPos>()
    val disallowUuids = mutableSetOf<UUID>()

    fun configure() {
        gameHandle.configureProtection {
            fun allowed(entity: Entity) =
                entity.uuid !in disallowUuids

            // only for allowed players
            allow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope { entity, _ ->
                allowed(entity)
            })

            allow(ProtectionTypes.USE_ITEM_ON_BLOCK, PlayerGenericScope<UseOnContext> { player, _ ->
                allowed(player)
            })

            allow(
                PlayerItemStackScope { player, _ ->
                    allowed(player)
                },
                ProtectionTypes.CONSUME_FOOD,
                ProtectionTypes.CRAFT_ITEM,
            )

            allow(
                EntityBlockScope { entity, _ ->
                    allowed(entity)
                },
                ProtectionTypes.USE_BLOCK,
                ProtectionTypes.CHARGE_RESPAWN_ANCHOR,
                ProtectionTypes.EAT_CAKE,
                ProtectionTypes.PICKUP_FLUID,
                ProtectionTypes.PLACE_BLOCKS,
                ProtectionTypes.BREAK_BLOCKS,
                ProtectionTypes.PLACE_FLUID,
                ProtectionTypes.PRIME_TNT,
            )

            allow(ProtectionTypes.DROP_ITEM, PlayerIntBoolScope { player, _, _ ->
                allowed(player)
            })

            allow(ProtectionTypes.HUNGER, PlayerScope { player ->
                allowed(player)
            })

            allow(ProtectionTypes.PICKUP_ITEM, PlayerItemEntityScope { player, _ ->
                allowed(player)
            })

            allow(ProtectionTypes.PICKUP_PROJECTILE, PlayerEntityScope<Projectile> { player, _ ->
                allowed(player)
            })

            // always allowed
            allow(ProtectionTypes.MODIFY_INVENTORY)
            allow(ProtectionTypes.SWAP_HAND_ITEMS)

            // generic
            allow(ProtectionTypes.EXPLOSION)
            allow(ProtectionTypes.ITEM_SCATTER)
        }

        // track player made blocks
        gameHandle.hooks.registerHook(
            BlockModificationHooks.BLOCK_PLACED,
            BlockModificationHooks.BlockModifiedHook { level, pos, entity ->
                if (level == world && entity is ServerPlayer && gameHandle.participants.isParticipating(entity)) {
                    playerMade.add(pos.immutable())
                }
            }
        )

        // only allow breaking player made blocks
        gameHandle.hooks.registerHook(
            BlockModificationHooks.BREAK_BLOCK,
            BlockModificationHooks.BlockModifyHook { level, pos, entity ->
                level != world || entity !is ServerPlayer || !gameHandle.participants.isParticipating(entity) || !playerMade.contains(pos)
            }
        )
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