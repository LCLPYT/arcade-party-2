package work.lclpnet.ap2.game.glowing_bomb

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.ticks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.glowing_bomb.data.GbAnchor
import work.lclpnet.ap2.game.glowing_bomb.data.GbBomb
import work.lclpnet.ap2.game.glowing_bomb.data.GbManager
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val BOMB_PASS_COST = 40
private const val INITIAL_CREDITS = BOMB_PASS_COST * 2
private const val MINIMUM_BOMB_PASS_TICKS = 10
private const val CREDITS_PER_TICK = 1

val BombAssigned = Stat("bomb_assigned", 0)
val BombPasses = Stat("bomb_passed", 0)
val BombExploded = Stat("bomb_exploded", 0)
val MaxSafeStreak = Stat("max_safe_streak", 0)
val BombHoldTime = Stat("bomb_hold_time", 0f, unit = StatUnits.Seconds)
val MinFuseOnPass = Stat("min_fuse_on_pass", 0f, higherIsBetter = false, unit = StatUnits.Seconds)

class GlowingBombInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrapFunction {

    private val random = Random()
    private val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val credits = Object2IntOpenHashMap<UUID>()
    private val safeStreak = Object2IntOpenHashMap<UUID>()
    private val holdTicks = Object2IntOpenHashMap<UUID>()
    private val stats = createStats(BombAssigned, BombPasses, BombExploded, MaxSafeStreak, BombHoldTime, MinFuseOnPass)
    private val initialPlayerCount = gameHandle.participants.count()
    private lateinit var manager: GbManager
    private lateinit var scene: Scene
    private var bomb: GbBomb? = null
    private var mayPass = false
    private var wasPassed = false
    private var time = 0
    private var fuseTicks = 0
    private var bombDelayedSpawn: TaskHandle? = null

    init {
        disableTeleportEliminated()
    }

    override fun getMapBootstrap(): MapBootstrap = ServerThreadMapBootstrap(this)

    override fun bootstrapWorld(world: ServerLevel, map: GameMap) {
        manager = GbManager(world, map, random, gameHandle.participants, ::onAnchorFilled)
        manager.setupAnchors()
    }

    override fun prepare() {
        val hooks = gameHandle.hooks

        movementBlocker.init(hooks)
        manager.teleportPlayers()

        for (player in gameHandle.participants) {
            movementBlocker.disableMovement(player)
        }

        useTaskDisplay()
    }

    override fun go() {
        val hooks = gameHandle.hooks

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            val serverPlayer = player as? ServerPlayer ?: return@registerWith InteractionResult.PASS
            if (!players().isParticipating(serverPlayer)) return@registerWith InteractionResult.PASS

            val stack = serverPlayer.getItemInHand(hand)

            if (stack.`is`(Items.GLOWSTONE)) {
                if (manager.hasBomb(serverPlayer) && !player.cooldowns.isOnCooldown(stack)) {
                    passBomb(serverPlayer)
                }
                return@registerWith InteractionResult.FAIL
            }

            InteractionResult.PASS
        }

        runEveryTick { tickCredits() }

        scene = Scene(ServerWorldMountContext(level))
        scene.animate(1, gameHandle.scheduler)

        // init min fuse pass to max value for everyone
        val noPassFuse = maxFuseTicks() / 20f
        for (player in players()) {
            stats.set(player, MinFuseOnPass, noPassFuse)
        }

        spawnBomb()
    }

    override fun onEliminated(player: ServerPlayer) {
        movementBlocker.enableMovement(player)
    }

    override fun participantRemoved(player: ServerPlayer) {
        manager.removeAnchorOf(player)
        super.participantRemoved(player)
    }

    private fun spawnBomb() {
        if (!manager.assignBomb()) {
            checkForWinner()
            return
        }

        val pos: Vec3 = manager.bombLocation() ?: return checkForWinner()

        bomb = GbBomb(scene, ::onBombYielded).also { b ->
            b.scale.set(0.4)
            b.position.set(pos.x(), pos.y(), pos.z())

            val amount = randomAmount()
            b.setGlowStoneAmount(amount, random)

            scene.add(b)
        }

        fuseTicks = randomFuseTicks()
        gameHandle.scheduler.timeout(fuseTicks) { -> bombTimerExpired() }

        val x = pos.x(); val y = pos.y(); val z = pos.z()

        level.playSound(null, x, y, z, SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 0.75f, 0.5f)
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 15, 0.1, 0.1, 0.1, 0.2)

        for (player in gameHandle.participants) {
            credits.put(player.uuid, INITIAL_CREDITS)
        }

        time = 0
        wasPassed = false
        mayPass = true

        manager.bombHolder().ifPresent { player ->
            stats.increment(player, BombAssigned)
            onAcquiredBomb(player)
        }
    }

    private fun randomFuseTicks(): Int {
        val minFuse = minFuseTicks()
        val maxFuse = maxFuseTicks()
        return random.nextInt(minFuse, maxFuse + 1)
    }

    private fun maxFuseTicks(): Int = when {
        initialPlayerCount <= 5 -> Ticks.seconds(18)
        initialPlayerCount <= 10 -> Ticks.seconds(14)  // avg 10s
        else -> Ticks.seconds(12)  // avg 8.75s
    }

    private fun minFuseTicks(): Int = when {
        initialPlayerCount <= 5 -> Ticks.seconds(7)
        initialPlayerCount <= 10 -> Ticks.seconds(6)
        else -> Ticks.seconds(5) + 10
    }

    private fun randomAmount(): Int = when {
        // 4 player worst case: 3 * 4 * 18s + 3 * 18s = 270s = 4.5min
        // 4 player avg case: 3 * 2 * 12.5s + 12.5s = 87.5s = 1.5min
        // 4 player best case: 3 * 2 * 7s = 42s
        initialPlayerCount <= 5 -> random.nextInt(1, 4)
        // 10 player worst case: 9 * 2 * 14s + 14s = 266s = 4.4min
        // 10 player avg case: 9 * 2 * 10s + 10s = 190s = 3.17min
        // 10 player best case: 9 * 2 * 6s = 108s = 1.8min
        initialPlayerCount <= 10 -> random.nextInt(2, 4)
        // 12 player worst case: 11 * 2 * 12s + 12s = 264s = 4.4min
        // 12 player avg case: 11 * 2 * 8.75s + 8.75s = 201s = 3.4min
        // 12 player best case: 11 * 5.5s = 60s = 1min
        else -> random.nextInt(2, 5)
    }

    private fun onAcquiredBomb(player: ServerPlayer) {
        val stack = ItemStack(Items.GLOWSTONE, bomb?.glowStoneAmount ?: 1)
        stack.set(DataComponents.CUSTOM_NAME, gameHandle.translations.translateText(player, "game.ap2.glowing_bomb.pass")
            .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })

        player.inventory.setItem(4, stack)
        PlayerInventoryAccess.setSelectedSlot(player, 4)

        val creditCount = credits.getOrDefault(player.uuid, 0)
        val cooldown = MINIMUM_BOMB_PASS_TICKS + maxOf(0, BOMB_PASS_COST - creditCount)
        val cooldownGroup = BuiltInRegistries.ITEM.getKey(Items.GLOWSTONE)

        player.cooldowns.addCooldown(cooldownGroup, cooldown)
    }

    private fun onPassedBomb(player: ServerPlayer) {
        player.inventory.setItem(4, ItemStack.EMPTY)
    }

    private fun passBomb(player: ServerPlayer) {
        if (winManager.isGameOver || !mayPass) return

        val uuid = player.uuid
        val creditCount = credits.getOrDefault(uuid, 0)

        if (creditCount < BOMB_PASS_COST) return

        credits.put(uuid, maxOf(0, creditCount - BOMB_PASS_COST))

        val nextHolder = manager.nextBombHolder() ?: player

        onPassedBomb(player)
        onAcquiredBomb(nextHolder)

        if (nextHolder == player) return

        stats.increment(player, BombPasses)

        val remainingFuse = (fuseTicks - time).coerceAtLeast(0) / 20f
        if (remainingFuse < stats.get(player, MinFuseOnPass)) {
            stats.set(player, MinFuseOnPass, remainingFuse)
        }

        wasPassed = true

        val pos = manager.bombLocation() ?: return

        val x = pos.x(); val y = pos.y(); val z = pos.z()
        bomb?.position?.set(x, y, z)
        level.playSound(null, x, y, z, SoundEvents.BEEHIVE_ENTER, SoundSource.HOSTILE, 0.75f, 1.4f)
    }

    private fun bombTimerExpired() {
        mayPass = false
        val holder = manager.bombHolder()
        holder.ifPresent(::onPassedBomb)
        holder.ifPresent(::onBombExploded)

        val b = bomb!!
        val pos: Vector3d = b.worldTranslation()
        val x = pos.x(); val y = pos.y(); val z = pos.z()

        level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 0.9f, 1.0f)
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0x8f509e), x, y, z, 1, 0.0, 0.0, 0.0, 1.0)
        level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.1, 0.1, 0.1, 0.1)

        val anchor = manager.bombAnchor() ?: return checkForWinnerOrNext()
        b.yieldGlowStone(manager, anchor)
    }

    private fun onBombExploded(holder: ServerPlayer) {
        stats.increment(holder, BombExploded)

        val explodedUuid = holder.uuid
        safeStreak.put(explodedUuid, 0)

        for (player in gameHandle.participants) {
            if (player.uuid == explodedUuid) continue

            val streak = safeStreak.getInt(player.uuid) + 1
            safeStreak.put(player.uuid, streak)
            stats.set(player, MaxSafeStreak, maxOf(stats.get(player, MaxSafeStreak), streak))
        }
    }

    private fun checkForWinnerOrNext() {
        if (gameHandle.participants.count() < 2) {
            checkForWinner()
            return
        }
        delayNextBomb()
    }

    private fun delayNextBomb() {
        bombDelayedSpawn?.cancel()
        bombDelayedSpawn = runAfter(4.seconds) { spawnBomb() }
    }

    private fun checkForWinner() {
        winManager.checkForLastRemaining()
    }

    private fun onBombYielded() {
        bomb?.let { b ->
            val pos: Vector3d = b.worldTranslation()
            val x = pos.x(); val y = pos.y(); val z = pos.z()

            level.playSound(null, x, y, z, SoundEvents.DECORATED_POT_INSERT, SoundSource.HOSTILE, 1f, 0f)
            level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.1, 0.1, 0.1, 0.1)

            scene.remove(b)
            bomb = null
        }

        delayNextBomb()
    }

    private fun onAnchorFilled(anchor: GbAnchor) {
        runAfter(30.ticks) {
            explodeAnchor(anchor)
        }
    }

    private fun explodeAnchor(anchor: GbAnchor) {
        if (winManager.isGameOver) return

        val pos = anchor.pos
        val x = pos.x() + 0.5; val y = pos.y() + 0.5; val z = pos.z() + 0.5

        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 200, 1.0, 1.0, 1.0, 0.5)
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.2f)

        manager.removeAnchor(anchor)

        val player = gameHandle.server.playerList.getPlayer(anchor.owner)

        if (player == null) {
            checkForWinnerOrNext()
            return
        }

        eliminate(player)

        if (winManager.isGameOver) return

        delayNextBomb()
    }

    private fun tickCredits() {
        // pause credit acquisition if the bomb is currently not passable
        if (!mayPass) return

        time++

        manager.bombHolder().ifPresent { player ->
            val uuid = player.uuid

            // accumulate the total time the player has held a bomb
            holdTicks.addTo(uuid, 1)
            stats.set(player, BombHoldTime, holdTicks.getInt(uuid) / 20f)

            // don't grant credits if the bomb wasn't passed yet and couldn't have exploded yet because of the minimum fuse time
            if (wasPassed || time >= minFuseTicks()) {
                credits.put(uuid, credits.getOrDefault(uuid, 0) + CREDITS_PER_TICK)
            }
        }
    }
}
