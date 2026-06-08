package work.lclpnet.ap2.game.hot_potato

import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam
import work.lclpnet.ap2.api.game.GameOverListener
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.FireworkEntityAccess
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.time.Duration.Companion.seconds

const val DURATION_SECONDS = 20
val MARK_PERIOD_DURATION = 6.seconds

val PotatoAssigned = Stat("potato_assigned", 0)
val TimesPassed = Stat("times_passed", 0)

class HotPotatoInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map), GameOverListener {

    private val random = Random()
    private lateinit var dynamicBossBar: DynamicTranslatedBossBar
    private var markedPlayer: ServerPlayer? = null
    private lateinit var team: PlayerTeam
    private var task: TaskHandle? = null
    private var markTask: TaskHandle? = null
    private val stats = createStats(PotatoAssigned, TimesPassed)

    override fun prepare() {
        winManager.addListener(this)
        dynamicBossBar = useRemainingPlayersDisplay()
        val scoreboardManager = gameHandle.scoreboardManager
        team = scoreboardManager.createTeam("team")
        team.color = ChatFormatting.DARK_RED
    }

    override fun go() {
        nextRound()

        val hooks = gameHandle.hooks

        PlayerInteractionHooks.ATTACK_ENTITY.registerWith(hooks) { player, _, _, entity, _ ->
            if (player is ServerPlayer && entity is ServerPlayer) {
                tryPassPotato(player, entity)
            }
            InteractionResult.PASS
        }

        PlayerInteractionHooks.USE_ENTITY.registerWith(hooks) { player, _, _, entity, _ ->
            if (player is ServerPlayer && entity is ServerPlayer) {
                tryPassPotato(player, entity)
            }
            InteractionResult.PASS
        }
    }

    private fun nextRound() {
        if (!markRandomPlayer()) {
            winManager.cancel()
            return
        }

        for (player in gameHandle.participants) {
            if (player == markedPlayer) continue
            glow(player, Ticks.seconds(3))
        }

        val bossBar = dynamicBossBar.bossBar
        val scheduler = gameHandle.scheduler

        markAndReschedule(scheduler)

        var t = 0
        var i = DURATION_SECONDS

        task = scheduler.interval({ info ->
            t++
            spawnParticles()

            if (t < 20) return@interval

            t = 0
            i--

            bossBar.setProgress(Mth.clamp(i.toFloat() / DURATION_SECONDS, 0f, 1f))

            if (i > 0) {
                spawnFirework(FireworkExplosion(
                    FireworkExplosion.Shape.SMALL_BALL,
                    IntList.of(0xff0000),
                    IntList.of(),
                    false,
                    false
                ), 1)
                return@interval
            }

            spawnFirework(FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0xff0000),
                IntList.of(0xfff200),
                false,
                true
            ), 0)

            info.cancel()
            eliminate(markedPlayer!!)
            bossBar.setProgress(1f)
        }, 1).whenComplete(::onRoundOver)
    }

    private fun markAndReschedule(scheduler: TaskScheduler) {
        for (player in gameHandle.participants) {
            glow(player, Ticks.seconds(1))
        }

        markTask = runAfter(MARK_PERIOD_DURATION) {
            markAndReschedule(scheduler)
        }
    }

    private fun onRoundOver() {
        markTask?.cancel()

        for (player in gameHandle.participants) {
            player.removeEffect(MobEffects.GLOWING)
        }

        if (winManager.isGameOver) return

        runAfter(3.seconds) {
            nextRound()
        }
    }

    override fun eliminate(player: ServerPlayer) {
        super.eliminate(player)
        removePotato(player)
        player.setGameMode(GameType.SPECTATOR)
        if (markedPlayer == player) markedPlayer = null
    }

    override fun participantRemoved(player: ServerPlayer) {
        super.participantRemoved(player)
        if (markedPlayer != player) return
        removePotato(player)
        markedPlayer = null
        task?.cancel()
        markTask?.cancel()
    }

    override fun onGameOver() {
        dynamicBossBar.bossBar.setProgress(1f)
        markedPlayer?.let { removePotato(it) }
        markedPlayer = null
    }

    private fun spawnParticles() {
        val player = markedPlayer?.takeIf { !it.hasDisconnected() } ?: return
        val x = player.x; val y = player.y; val z = player.z
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.0, 0.2, 0.0)
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.2, 0.2, 0.2, 0.1)
    }

    private fun spawnFirework(explosion: FireworkExplosion, delay: Int) {
        val player = markedPlayer?.takeIf { !it.hasDisconnected() } ?: return
        val x = player.x; val y = player.y; val z = player.z

        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        rocket.set(DataComponents.FIREWORKS, Fireworks(1, listOf(explosion)))

        val firework = FireworkRocketEntity(level, x, y + 3, z, rocket)
        level.addFreshEntity(firework)

        if (delay > 0) {
            gameHandle.rootScheduler.timeout(delay) { ->
                FireworkEntityAccess.explode(firework)
            }
        } else {
            FireworkEntityAccess.explode(firework)
        }
    }

    private fun markRandomPlayer(): Boolean {
        val randomPlayer = gameHandle.participants.getRandomParticipant(random)
        if (randomPlayer.isEmpty) return false
        val player = randomPlayer.get()
        markPlayer(player)
        stats.increment(player, PotatoAssigned)
        return true
    }

    private fun markPlayer(player: ServerPlayer) {
        markedPlayer?.let { removePotato(it) }
        markedPlayer = player
        addPotato(player)
    }

    private fun addPotato(player: ServerPlayer) {
        val translations = gameHandle.translations
        val stack = ItemStack(Items.BAKED_POTATO)

        stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.hot_potato.item")
            .styled { it.withColor(0xff0000).withItalic(false) })

        player.inventory.setItem(4, stack)

        PlayerInventoryAccess.setSelectedSlot(player, 4)

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.RED_WOOL))

        player.addEffect(MobEffectInstance(MobEffects.SPEED, DURATION_SECONDS * 20, 1, false, false, false))

        glow(player, DURATION_SECONDS * 20)

        val title = translations.translateText(player, "game.ap2.hot_potato.title")
            .styled { it.withColor(0xff0000).withBold(true) }

        val subtitle = translations.translateText(player, "game.ap2.hot_potato.subtitle")
            .formatted(ChatFormatting.RED)

        Title.get(player).title(title, subtitle, 2, 10, 2)

        gameHandle.scoreboardManager.joinTeam(player, team)
    }

    private fun removePotato(player: ServerPlayer) {
        player.inventory.clearContent()
        player.removeAllEffects()
        Title.get(player).clear()
        gameHandle.scoreboardManager.leaveTeam(player, team)
    }

    private fun tryPassPotato(player: ServerPlayer, target: ServerPlayer) {
        if (player != markedPlayer) return
        if (!gameHandle.participants.isParticipating(target)) return
        markPlayer(target)
        stats.increment(player, TimesPassed)
    }
}

private fun glow(player: ServerPlayer, ticks: Int) {
    player.addEffect(MobEffectInstance(MobEffects.GLOWING, ticks, 1, false, false, false))
}
