package work.lclpnet.ap2.game.treasure_hunter

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import java.util.Random

class TreasureHunterInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle) {

    private val foundChest = OrderedDataContainer<ServerPlayer, PlayerRef>(PlayerRef::create)
    private val score = IntScoreDataContainer<ServerPlayer, PlayerRef>(PlayerRef::create)
    private val data = CombinedDataContainer<ServerPlayer, PlayerRef>(listOf(foundChest, score))
    private val materials = mutableSetOf<BlockState>()
    private val random = Random()

    init {
        useSurvivalMode()
    }

    override fun getData(): DataContainer<ServerPlayer, PlayerRef> = data

    override fun prepare() {
        commons().gameRuleBuilder()
            .set(GameRules.BLOCK_DROPS, false)
            .set(GameRules.ENTITY_DROPS, false)

        MapUtil.readBlockStates(map.requireProperty("materials"), materials, gameHandle.logger)

        val participants = gameHandle.participants
        val translations = gameHandle.translations

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, world, _, hitResult ->
            if (player !is ServerPlayer || !participants.isParticipating(player)
                || !world.getBlockState(hitResult.blockPos).`is`(Blocks.CHEST)) {
                return@registerWith InteractionResult.PASS
            }

            if (winManager.isGameOver) return@registerWith InteractionResult.FAIL

            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.8f)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.2f, 0.5f)

            val scoreEntry = score.getEntry(player)
                .map { it.toText(translations) }
                .orElse(Component.literal("-"))

            val detail = translations.translateText("game.ap2.treasure_hunter.found_treasure", scoreEntry)
            foundChest.add(player, detail)
            winManager.complete()

            InteractionResult.SUCCESS_SERVER
        }

        PlayerInteractionHooks.BREAK_BLOCK.registerWith(hooks) { world, player, pos, state, _ ->
            if (player !is ServerPlayer || !participants.isParticipating(player)) {
                return@registerWith true
            }
            if (state !in materials || random.nextFloat() >= COIN_CHANCE) return@registerWith true
            spawnCoin(pos, world)
            true
        }

        placeChest()
        useTaskDisplay()
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
                materials.contains(entity.level().getBlockState(pos))
            }
            ProtectionTypes.PICKUP_ITEM.allow(config) { player, item ->
                if (player is ServerPlayer && item.item.`is`(Items.SUNFLOWER)) {
                    item.discard()
                    giveCoin(player)
                }
                false
            }
        }
        giveShovelsToPlayers()
    }

    private fun spawnCoin(pos: BlockPos, world: Level) {
        val coin = ItemEntity(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), ItemStack(Items.SUNFLOWER))
        world.addFreshEntity(coin)
    }

    private fun giveCoin(player: ServerPlayer) {
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.7f, 1.55f)
        commons().addScore(player, 1, score)
    }

    private fun giveShovelsToPlayers() {
        val efficiency = ItemHelper.getEnchantment(Enchantments.EFFICIENCY, world.registryAccess())
        for (player in gameHandle.participants) {
            val stack = unbreakable(ItemStack(Items.IRON_SHOVEL))
            stack.enchant(efficiency, 4)
            player.inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
        }
    }

    private fun placeChest() {
        val box = MapUtil.readBox(map.requireProperty("chest-area"))
        val candidates = mutableListOf<BlockPos>()
        for (block in box) {
            if (world.getBlockState(block) in materials) {
                candidates.add(block.immutable())
            }
        }
        val chestPos = candidates[random.nextInt(candidates.size)]
        world.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState())
    }
}

private const val COIN_CHANCE = 0.025f
