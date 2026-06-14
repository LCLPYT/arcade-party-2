package work.lclpnet.ap2.game.spleef

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
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
import work.lclpnet.ap2.api.stats.CommonStats.BlocksBroken
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.FallKillTracker
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.hook.util.OnGroundDetector
import work.lclpnet.kibu.scheduler.Ticks
import kotlin.time.Duration.Companion.seconds

val WORLD_BORDER_DELAY = Ticks.seconds(40).toLong()
const val WORLD_BORDER_SHRINK_PER_SECOND = 1.0

class SpleefInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map) {

    private val stats = createStats(TimeSurvived, Kills, BlocksBroken, DistanceMoved)
    private val killTracker = FallKillTracker(gameHandle.participants).also {
        it.init(gameHandle.scheduler)
    }
    private var breakableBlocks = readBreakableBlocks()
    private var snowArea = MapUtil.readBox(map.requireProperty("snow-area"))
    private var frost = map.properties.optBoolean("frost", false)

    init {
        useSurvivalMode()
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

    override fun prepare() {
        useSmoothDeath()
        useNoHealing()
        useRemainingPlayersDisplay()

        trackSurvivalTime(stats)
        trackDistanceMoved(stats)
    }

    override fun teleportPlayers() {
        val scanBox = snowArea.translate(Vec3i(0, 1, 0))
        val scanStart = BlockPos.containing(MapUtils.getSpawnPosition(map))
        val spacing = map.properties.optNumber("spawn-spacing", 8.0).toDouble()

        teleportToRandomSpawns(scanBox, listOf(scanStart), spacing)
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
                isBreakable(entity.level().getBlockState(pos))
            }

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                isDamageAllowed(damageSource)
            }
        }

        giveShovelsToPlayers()

        BlockModificationHooks.BLOCK_BROKEN.registerWith(gameHandle.hooks) { _, pos, entity ->
            if (entity is ServerPlayer && gameHandle.participants.isParticipating(entity)) {
                killTracker.onBlockBroken(pos, entity)
                stats.increment(entity, BlocksBroken)
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
        
        runEveryTick {
            checkValidPositions()
        }
    }

    /**
     * Checks that every player stays only on breakable blocks at all times.
     */
    fun checkValidPositions() {
        if (winManager.gameOver) return
        
        for (player in players()) {
            if (!OnGroundDetector.isOnGroundServer(player) || player.isInLava || player.isInPowderSnow) continue

            val box = player.boundingBox.setMinY(player.y - 0.02).setMaxY(player.y + 1e-5)

            for (pos in BlockPos.betweenClosed(box)) {
                val state = level.getBlockState(pos)

                if (state.getCollisionShape(level, pos).isEmpty) continue

                if (!isBreakable(state)) {
                    eliminate(player)
                }
            }
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

    override fun onEliminated(player: ServerPlayer) {
        super.onEliminated(player)

        SoundHelper.playSoundAt(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.LAVA, 100, 0.5, 0.5, 0.5, 0.2)
    }

    private fun removeBlocks() {
        val air = Blocks.AIR.defaultBlockState()

        for (pos in BlockPos.betweenClosed(snowArea.first(), snowArea.second())) {
            val state = level.getBlockState(pos)

            if (isBreakable(state)) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS)
            }
        }

        playSound(SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f)
    }

    private fun isBreakable(state: BlockState): Boolean = breakableBlocks.contains(state.block)

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
