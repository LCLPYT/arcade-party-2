package work.lclpnet.ap2.game.mining_battle

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.DataContainers
import work.lclpnet.ap2.impl.game.data.IntDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import java.util.*

private const val DURATION_SECONDS = 60

class MiningBattleInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrapFunction {

    private val data: IntDataContainer<ServerPlayer, PlayerRef> =
        DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val ore = MiningBattleOre(Random(), gameHandle, ::onGainPoints, ::canBeMined)
    private val material: MutableSet<BlockState> = HashSet()
    private lateinit var box: BlockBox

    init {
        useSurvivalMode()
    }

    override fun getMapBootstrap(): MapBootstrap = ServerThreadMapBootstrap(this)

    override fun bootstrapWorld(world: ServerLevel, map: GameMap) {
        val gameRules = world.gameRules

        gameRules.set(GameRules.BLOCK_DROPS, false, gameHandle.server)

        placeOres(world, map)
    }

    private fun placeOres(world: ServerLevel, map: GameMap) {
        ore.init()

        box = MapUtil.readBox(map.requireProperty("mining-box"))

        material.clear()
        MapUtil.readBlockStates(map.requireProperty("material"), material, gameHandle.logger)

        MiningBattleGenerator(ore, box, material).generateOre(world)
    }

    override fun prepare() {
        giveItems()
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.BREAK_BLOCKS.allow(config) { _, pos -> canBeMined(pos) }
        }

        val hooks = gameHandle.hooks
        val participants = gameHandle.participants

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { world, pos, entity ->
            if (entity !is ServerPlayer || !participants.isParticipating(entity)
                || winManager.isGameOver || isOutsideMiningArea(pos)) return@registerWith false

            val state = world.getBlockState(pos)

            if (ore.isOre(state)) {
                ore.onOreBroken(entity, pos, state)
            }

            false
        }

        val subject = gameHandle.translations.translateText(gameHandle.gameInfo.taskKey)

        commons().createTimer(subject, DURATION_SECONDS).whenDone(winManager::complete)
    }

    private fun onGainPoints(player: ServerPlayer, points: Int) {
        commons().addScore(player, points, data)

        if (points <= 1) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.5f, 2f)
        } else if (points == 2) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 2f)
        } else if (points < 5) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.3f, 1f)
        } else {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 0.5f, 1f)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.325f, 1.2f)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.225f, 0f)
        }
    }

    private fun giveItems() {
        val efficiency = ItemHelper.getEnchantment(Enchantments.EFFICIENCY, world.registryAccess())

        for (player in gameHandle.participants) {
            val pickaxe = unbreakable(ItemStack(Items.DIAMOND_PICKAXE))
            pickaxe.enchant(efficiency, 3)

            pickaxe.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(pickaxe).withStyle { style ->
                style.applyFormat(ChatFormatting.GOLD).withItalic(false)
            })

            player.inventory.setItem(4, pickaxe)
        }
    }

    private fun isOutsideMiningArea(pos: BlockPos): Boolean =
        !box.contains(pos)

    private fun canBeMined(pos: BlockPos): Boolean {
        if (isOutsideMiningArea(pos)) return false

        val state = world.getBlockState(pos)

        return material.contains(state) || ore.isOre(state)
    }

    override fun getData(): DataContainer<ServerPlayer, PlayerRef> = data
}
