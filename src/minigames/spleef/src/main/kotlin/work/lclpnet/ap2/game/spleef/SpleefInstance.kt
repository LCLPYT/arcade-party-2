package work.lclpnet.ap2.game.spleef

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.json.JSONArray
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.FallKillTracker
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.scheduler.Ticks
import kotlin.time.Duration.Companion.seconds

val WORLD_BORDER_DELAY = Ticks.seconds(40).toLong()
const val WORLD_BORDER_SHRINK_PER_SECOND = 1.0

class SpleefInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    private val stats = createStats(
        CommonStats.TimeSurvived,
        CommonStats.Kills,
        CommonStats.BlocksBroken,
        CommonStats.DistanceMoved,
    )
    private lateinit var killTracker: FallKillTracker
    private lateinit var breakableBlocks: Set<Block>
    private var frost = false

    init {
        useSurvivalMode()
    }

    override fun prepare() {
        frost = map.properties.optBoolean("frost", false)
        breakableBlocks = readBreakableBlocks()

        useSmoothDeath()
        useNoHealing()
        useRemainingPlayersDisplay()

        trackSurvivalTime(stats)
        trackDistanceMoved(stats)
    }

    private fun readBreakableBlocks(): Set<Block> {
        val blocksArray = map.properties.optJSONArray("breakable-blocks") ?: JSONArray()
        val stateList = mutableListOf<BlockState>()
        MapUtil.readBlockStates(blocksArray, stateList, logger)

        if (stateList.isEmpty()) {
            stateList.add(Blocks.SNOW_BLOCK.defaultBlockState())
        }

        return stateList.map { it.block }.toSet()
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
                breakableBlocks.contains(entity.level().getBlockState(pos).block)
            }

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                isDamageAllowed(damageSource)
            }
        }

        giveShovelsToPlayers()

        killTracker = FallKillTracker(gameHandle.participants)
        killTracker.init(gameHandle.scheduler)

        BlockModificationHooks.BLOCK_BROKEN.registerWith(gameHandle.hooks) { _, pos, entity ->
            if (entity is ServerPlayer && gameHandle.participants.isParticipating(entity)) {
                killTracker.onBlockBroken(pos, entity)
                stats.increment(entity, CommonStats.BlocksBroken)
            }
        }

        val worldBorderConfig = commons().readWorldBorderConfig()
        val shrinkDurationSeconds = (worldBorderConfig.maxRadius / WORLD_BORDER_SHRINK_PER_SECOND).seconds

        commons().scheduleWorldBorderShrink(
            WORLD_BORDER_DELAY,
            shrinkDurationSeconds.inWholeTicks,
            Ticks.seconds(5).toLong()
        ).then(::removeBlocks)

        commons().whenBelowCriticalHeight().then { player ->
            val source = if (frost) player.damageSources().freeze() else player.damageSources().fellOutOfWorld()

            player.hurtServer(level, source, player.health)
        }
    }

    private fun isDamageAllowed(damageSource: DamageSource): Boolean = damageSource.isOf(DamageTypes.LAVA)
            || damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
            || (frost && damageSource.isOf(DamageTypes.FREEZE))

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        super.onDeath(player, attacker)

        val killerId = killTracker.getKiller(player)

        if (killerId != null) {
            val killer = gameHandle.server.playerList.getPlayer(killerId)

            if (killer != null && killer != player) {
                gainKill(killer, stats)
                player.setLastHurtByPlayer(killer, 100)
            }
        }

        killTracker.forget(player)
    }

    private fun removeBlocks() {
        val air = Blocks.AIR.defaultBlockState()
        val box = MapUtil.readBox(map.requireProperty("snow-area"))

        for (pos in BlockPos.betweenClosed(box.first(), box.second())) {
            if (breakableBlocks.contains(level.getBlockState(pos).block)) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS)
            }
        }

        SoundHelper.playSound(gameHandle.server, SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f)
    }

    private fun giveShovelsToPlayers() {
        val translations = gameHandle.translations

        for (player in gameHandle.participants) {
            val stack = ItemStack(Items.IRON_SHOVEL).unbreakable()

            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.spleef.shovel")
                .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })

            player.inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
        }
    }
}
