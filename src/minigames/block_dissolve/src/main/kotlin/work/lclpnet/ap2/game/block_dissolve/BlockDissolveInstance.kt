package work.lclpnet.ap2.game.block_dissolve

import it.unimi.dsi.fastutil.longs.LongArrayList
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.KnockbackKillTracker
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import java.util.*

private const val SNOWBALL_SECONDS = 6
private const val MAX_SNOWBALLS = 6
private const val WARNING_DELAY_TICKS = 70
private const val WARNING_PERIOD_TICKS = 5
private const val WARNING_AMOUNT = 150

class BlockDissolveInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    private val markedBlocks = LongArrayList()
    private val random = Random()
    private val stats = createStats(TimeSurvived, Kills, DistanceMoved)
    private var nextSnowball = Ticks.seconds(3)
    private var tickOfSecond = 0
    private var extraDissolvedThisSecond = 0
    private var totalTime = 0
    private var time = 0
    private var dps = 0
    private var warningTimer = 0
    private var warning = false
    private var physics = false
    private lateinit var killTracker: KnockbackKillTracker

    init {
        useOldCombat()
    }

    override fun prepare() {
        commons().gameRuleBuilder()
            .set(GameRules.RANDOM_TICK_SPEED, 0)
            .set(GameRules.SPREAD_VINES, false)

        useNoHealing()
        useSmoothDeath()
        useRemainingPlayersDisplay()

        gameHandle.protect { config ->
            ProtectionTypes.MOUNT.allow(config)
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, source -> source.directEntity is Projectile }
        }

        val physicsProperty = map.getProperty<Any>("block_physics")

        if (physicsProperty is Boolean) {
            physics = physicsProperty
        }

        scanWorld()

        trackSurvivalTime(stats)
        trackDistanceMoved(stats)
    }

    override fun go() {
        killTracker = KnockbackKillTracker(gameHandle.participants)
        killTracker.init(gameHandle.scheduler)

        ProjectileHitEntityCallback.HOOK.registerWith(gameHandle.hooks) { projectile, hit ->
            val shooter = projectile.owner as? ServerPlayer ?: return@registerWith
            val victim = hit.entity as? ServerPlayer ?: return@registerWith
            val participants = gameHandle.participants

            if (participants.isParticipating(victim) && participants.isParticipating(shooter)) {
                killTracker.onHit(victim, shooter)
            }
        }

        commons().whenBelowCriticalHeight().then { player ->
            killTracker.getLastAttacker(player)?.let { it as? ServerPlayer }?.let { killer ->
                gainKill(killer, stats)
            }

            eliminate(player, killTracker.killMessage(player, gameHandle.deathMessages))
        }

        startDissolve()
    }

    private fun scanWorld() {
        val bounds = MapUtil.readBox(map.requireProperty("bounds"))
        val world = level

        for (pos in bounds) {
            val state = world.getBlockState(pos)
            if (state.isAir) continue
            markedBlocks.add(pos.asLong())
        }
    }

    private fun startDissolve() {
        gameHandle.scheduler.interval(this::tick, 1)
    }

    private fun tick(info: RunningTask) {
        val server = gameHandle.server

        totalTime++

        if (nextSnowball-- <= 0) {
            nextSnowball = Ticks.seconds(SNOWBALL_SECONDS)
            giveSnowball()
        }

        if (warning) {
            warningTimer++

            if (warningTimer % WARNING_PERIOD_TICKS == 0) {
                for (player in PlayerLookup.all(server)) {
                    ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 0f)
                }
            }

            if (warningTimer >= WARNING_DELAY_TICKS) {
                warning = false
                warningTimer = 0

                for (player in PlayerLookup.all(server)) {
                    ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 1f, 0f)
                }

                repeat(WARNING_AMOUNT) { dissolveBlock() }
            }
        }

        val changeDps = when {
            dps < 20 -> totalTime % 45 == 0
            dps < 30 -> totalTime % 60 == 0
            else -> totalTime % 85 == 0
        }

        if (changeDps) {
            if (dps > 20 && dps % 5 == 0 && markedBlocks.size >= 500) {
                warning = true
            }
            dps++
        }

        if (markedBlocks.isEmpty) {
            info.cancel()
            return
        }

        tickOfSecond++

        if (++time >= (20f / dps).toInt()) {
            time = 0
            dissolveBlock()

            val extra = dps - 20

            if (extraDissolvedThisSecond < extra) {
                val amount = if (tickOfSecond == 20) {
                    extra - extraDissolvedThisSecond
                } else {
                    (dps / 20f).toInt()
                }

                repeat(amount) { dissolveBlock() }
                extraDissolvedThisSecond += amount
            }
        }

        if (tickOfSecond >= 20) {
            tickOfSecond = 0
            extraDissolvedThisSecond = 0
        }
    }

    private fun dissolveBlock() {
        val size = markedBlocks.size
        if (size == 0) return

        val idx = random.nextInt(size)
        val pos = BlockPos.of(markedBlocks.removeLong(idx))
        val world = level

        var flags = Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS
        flags = flags or if (physics) Block.UPDATE_NEIGHBORS else Block.UPDATE_KNOWN_SHAPE

        world.setBlock(pos, Blocks.AIR.defaultBlockState(), flags)

        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5

        world.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 0.2, 0.2, 0.2, 0.1)
        world.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.75f)
    }

    private fun giveSnowball() {
        for (player in gameHandle.participants) {
            giveSnowball(player)
        }
    }

    private fun giveSnowball(player: ServerPlayer) {
        val inventory = player.inventory
        var stack = inventory.getItem(0)

        if (!stack.isOf(Items.SNOWBALL)) {
            stack = ItemStack(Items.SNOWBALL)
        } else if (stack.count >= MAX_SNOWBALLS) {
            return
        } else {
            stack.grow(1)
        }

        inventory.setItem(0, stack)
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 2f)
    }
}
