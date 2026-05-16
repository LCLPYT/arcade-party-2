package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.vehicle.boat.Boat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.api.game.team.TeamSpawnAccess
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.cozy_campfire.MOVEMENT_SPEED
import work.lclpnet.gaco.collisions.CollisionDetector
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver
import work.lclpnet.game.api.prot.ProtectionConfig
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.PlayerReset
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.translate.Translations

class CCHooks(
    private val participants: Participants,
    private val teamManager: TeamManager,
    private val spawnAccess: TeamSpawnAccess,
    private val translations: Translations,
    private val args: Args
) {

    fun configure(config: ProtectionConfig) {
        val fuel = args.fuel
        val baseManager = args.baseManager

        config.allow(ProtectionTypes.PICKUP_ITEM, ProtectionTypes.SWAP_HAND_ITEMS, ProtectionTypes.PICKUP_PROJECTILE)

        ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, _ ->
            if (entity is ServerPlayer) participants.isParticipating(entity) && !baseManager.isInBase(entity)
            else entity is Boat
        }

        ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
            entity is ServerPlayer && fuel.isFuel(entity, pos)
        }

        ProtectionTypes.BLOCK_ITEM_DROP.allow(config) { _, _, itemStack -> fuel.isFuel(itemStack) }

        ProtectionTypes.DROP_ITEM.allow(config) { player, slot, inInventory ->
            if (inInventory || slot < 0 || slot > 8) return@allow true
            fuel.isFuel(player.inventory.getItem(slot))
        }

        ProtectionTypes.MODIFY_INVENTORY.allow(config) { clickEvent ->
            val slot = clickEvent.slot()
            if (slot in 5..8) return@allow false
            if (clickEvent.isDropAction()) {
                return@allow fuel.isFuel(PlayerUtils.getCursorStack(clickEvent.player()))
            }
            true
        }

        ProtectionTypes.USE_BLOCK.allow(config) { entity, pos ->
            onUseBlock(entity, pos)
            false
        }

        ProtectionTypes.ENTITY_ITEM_DROP.allow(config) { _, itemEntity -> fuel.isFuel(itemEntity.item) }

        ProtectionTypes.USE_ITEM_ON_BLOCK.allow(config) { _, obj -> obj.itemInHand.isOf(Items.LADDER) }
        ProtectionTypes.PLACE_BLOCKS.allow(config) { _, _ -> true }
    }

    fun register(hooks: HookRegistrar) {
        PlayerSpawnLocationCallback.HOOK.registerWith(hooks, ::onSpawnLocation)

        ServerLivingEntityHooks.ALLOW_DEATH.registerWith(hooks) { entity, _, _ ->
            if (entity is ServerPlayer) onDeath(entity)
            true
        }

        PlayerInteractionHooks.USE_ENTITY.registerWith(hooks) { player, _, hand, entity, _ ->
            if (player is ServerPlayer) onUseEntity(player, hand, entity)
            InteractionResult.FAIL
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, amount ->
            if (source.`is`(DamageTypes.FREEZE) && amount < Float.MAX_VALUE && entity.level() is ServerLevel) {
                entity.hurtServer(entity.level() as ServerLevel, entity.damageSources().freeze(), Float.MAX_VALUE)
                return@registerWith false
            }
            true
        }

        BlockModificationHooks.PLACE_BLOCK.registerWith(hooks) { _, _, _, state -> !state.isOf(Blocks.LADDER) }
    }

    fun configureBaseRegionEvents(collisions: CollisionDetector, observer: PlayerMovementObserver) {
        for ((team, base) in args.baseManager.getBases()) {
            val bounds = base.bounds
            collisions.add(bounds)
            observer.whenEntering(bounds) { player -> onEnterBaseOf(player, team) }
            observer.whenLeaving(bounds) { player -> onLeaveBaseOf(player, team) }
        }
    }

    private fun onUseEntity(player: ServerPlayer, hand: InteractionHand, entity: Entity) {
        val stack = player.getItemInHand(hand)
        if (!args.fuel.isFuel(stack)) return

        val team = args.baseManager.getEntityTeam(entity) ?: return
        if (!teamManager.isTeamMember(player, team)) return

        val base = args.baseManager.getBase(team) ?: return
        args.fuelListener.onAddFuel(player, base.campfirePos, team, stack)
    }

    private fun onUseBlock(entity: Entity, pos: BlockPos) {
        if (entity !is ServerPlayer) return

        val state = entity.level().getBlockState(pos)
        if (!state.isIn(BlockTags.CAMPFIRES)) return

        val stack = getHeldFuel(entity) ?: return

        val team = args.baseManager.getCampfireTeam(pos) ?: return
        if (!teamManager.isTeamMember(entity, team)) return

        args.fuelListener.onAddFuel(entity, pos, team, stack)
    }

    private fun getHeldFuel(player: ServerPlayer): ItemStack? {
        val fuel = args.fuel
        var stack = player.mainHandItem

        if (fuel.isFuel(stack)) return stack

        if (!stack.isEmpty) return null

        stack = player.offhandItem

        if (fuel.isFuel(stack)) return stack

        return null
    }

    private fun onSpawnLocation(data: PlayerSpawnLocationCallback.LocationData) {
        if (data.isJoin) return

        val player = data.player
        val team = teamManager.getTeam(player).orElse(null) ?: return
        val spawn = spawnAccess.getSpawn(team) ?: return

        data.position = Vec3(spawn.x(), spawn.y(), spawn.z())
        data.yaw = spawn.yaw
        data.pitch = spawn.pitch

        args.kitManager.giveItems(player)
        PlayerReset.modifyWalkSpeed(player, MOVEMENT_SPEED)
    }

    private fun onDeath(player: ServerPlayer) {
        val inventory: Inventory = player.inventory
        val fuel = args.fuel

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (fuel.isFuel(stack)) continue
            inventory.removeItemNoUpdate(i)
        }
    }

    private fun onEnterBaseOf(player: ServerPlayer, team: Team) {
        if (teamManager.isTeamMember(player, team)) return

        val name = translations.translateText(player, team.key().translationKey)
            .styled { it.withColor(team.key().color()) }

        val msg = Component.literal("⚠")
            .append(translations.translateText(player, "game.ap2.cozy_campfire.base_of", name))
            .append("⚠").withStyle { it.withColor(0xff0000) }

        player.sendOverlayMessage(msg)
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.5f, 1.2f)
    }

    private fun onLeaveBaseOf(player: ServerPlayer, team: Team) {
        if (teamManager.isTeamMember(player, team)) return
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.5f, 0.8f)
    }

    data class Args(
        val fuel: CCFuel,
        val baseManager: CCBaseManager,
        val kitManager: CCKitManager,
        val fuelListener: CCFuelListener
    )
}
