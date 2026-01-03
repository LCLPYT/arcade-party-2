package work.lclpnet.ap2.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.kibu.hook.world.BlockModificationHooks
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes

class PvpBehavior(
    val gameHandle: MiniGameHandle,
    val world: ServerLevel,
) {
    val playerMade = mutableSetOf<BlockPos>()

    fun configure() {
        gameHandle.protect {
            it.allow(
                ProtectionTypes.ALLOW_DAMAGE,
                ProtectionTypes.EXPLOSION,
                ProtectionTypes.USE_BLOCK,
                ProtectionTypes.USE_ITEM_ON_BLOCK,
                ProtectionTypes.CONSUME_FOOD,
                ProtectionTypes.CHARGE_RESPAWN_ANCHOR,
                ProtectionTypes.CRAFT_ITEM,
                ProtectionTypes.DROP_ITEM,
                ProtectionTypes.EAT_CAKE,
                ProtectionTypes.HUNGER,
                ProtectionTypes.ITEM_SCATTER,
                ProtectionTypes.MODIFY_INVENTORY,
                ProtectionTypes.PICKUP_FLUID,
                ProtectionTypes.PICKUP_ITEM,
                ProtectionTypes.PICKUP_PROJECTILE,
                ProtectionTypes.PLACE_BLOCKS,
                ProtectionTypes.BREAK_BLOCKS,
                ProtectionTypes.PLACE_FLUID,
                ProtectionTypes.PRIME_TNT,
                ProtectionTypes.SWAP_HAND_ITEMS,
            )
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
}