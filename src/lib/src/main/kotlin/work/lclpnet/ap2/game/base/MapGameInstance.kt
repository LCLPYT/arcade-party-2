package work.lclpnet.ap2.game.base

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.level.GameType
import org.json.JSONArray
import org.json.JSONObject
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.game.PlayerUtil
import work.lclpnet.ap2.impl.util.TranslationUtil
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.ap2.impl.util.effect.ApEffect
import work.lclpnet.ap2.impl.util.effect.ApEffects
import work.lclpnet.ap2.impl.util.property.ApMapProperties
import work.lclpnet.ap2.util.SubtitleCountdown
import work.lclpnet.combatctl.impl.CombatStyles
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/** A game instance that:
 * - is played on a map
 * - provides the default onPrepare() and onReady() entry points with the countdown in between
 * - provides common mini-game behaviour configuration methods
 * - configures restrictive protection with bypass for creative operator players
 * - registers default hooks, e.g. for spectators, spawn location and map properties
 *
 * Note that this game instance is not bound be of a specific type, i.e. subclasses can be ob type FFA, TEAM etc. */
abstract class MapGameInstance(
    val gameHandle: MiniGameHandle,
    val level: ServerLevel,
    val map: GameMap
) : MiniGameInstance {
    protected val mapProperties: ApMapProperties = ApMapProperties()

    @Volatile
    private var commons: GameCommons? = null
    private var countdownTime = 0
    private var countdownValue = 0
    private val activeEffects: MutableSet<ApEffect> = mutableSetOf()
    private var locatorBarEnabled = false

    override fun start() {
        gameHandle.protect { config ->
            config.disallowAll()
            ProtectorUtils.allowCreativeOperatorBypass(config)
        }

        registerDefaultHooks()

        onMapReady()
    }

    protected fun onMapReady() {
        applyMapEffects()
        loadMapProperties()
        configureLocatorBar()

        resetPlayers()
        teleportPlayers()

        sendMapCredits()

        gameHandle.deathMessages.replaceVanillaDeathMessages(level, hooks)

        prepare()

        val initialDelay = this.initialDelay

        val countdown = SubtitleCountdown(
            gameHandle.server,
            gameHandle.scheduler,
            { _ -> },
            { PlayerLookup.all(gameHandle.server) }
        )

        countdown.schedule(initialDelay) { this.afterInitialDelay() }
    }

    protected open fun teleportPlayers() {
        val spawn = MapUtils.getSpawnPosition(map)
        val yaw = MapUtils.getSpawnYaw(map)

        for (player in PlayerLookup.all(gameHandle.server)) {
            player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), emptySet(), yaw, 0f, true)
        }
    }

    private fun configureLocatorBar() {
        if (locatorBarEnabled) return

        // hide players from locator by default
        PlayerWaypointCallback.HOOK.registerWith(gameHandle.hooks) { _, waypoint ->
            waypoint is ServerPlayer
        }

        level.waypointManager.breakAllConnections()
    }

    private fun sendMapCredits() {
        val dataManager = gameHandle.dataManager

        val name = TranslationUtil.quote { lang ->
            Component.literal(map.getName(lang)).withStyle(
                ChatFormatting.AQUA, ChatFormatting.BOLD
            )
        }

        val authors = Component.literal(map.getAuthors().joinToString(", ") { str ->
            dataManager.string(str)
        }).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)

        translate("ap2.map.by", name, authors)
            .formatted(ChatFormatting.GREEN, ChatFormatting.BOLD)
            .sendTo(level.players())
    }

    private fun scheduleCountdown(durationTicks: Int) {
        countdownValue = min(3, durationTicks / 20)

        if (countdownValue <= 0) return

        runEvery(1.ticks, after = durationTicks.ticks - countdownValue.seconds) {
            tickCountdown(this)
        }.whenComplete {
            clearCountdown()
        }
    }

    private fun tickCountdown(task: RunningTask) {
        val time = countdownTime++

        if (time % 20 != 0) return

        if (countdownValue <= 0) {
            task.cancel()
        }

        val color = when (countdownValue) {
            3 -> ChatFormatting.RED
            2 -> ChatFormatting.GOLD
            1 -> ChatFormatting.YELLOW
            else -> ChatFormatting.GREEN
        }

        val msg = Component.literal((countdownValue--).toString()).withStyle(color, ChatFormatting.BOLD)

        for (player in PlayerLookup.all(gameHandle.server)) {
            player.sendOverlayMessage(msg)
        }
    }

    private fun clearCountdown() {
        for (player in allPlayers()) {
            player.sendOverlayMessage(Component.empty())
        }
    }

    private fun loadMapProperties() {
        val prop = map.getProperty<Any?>("properties")

        if (prop !is JSONObject) return

        val logger = gameHandle.logger

        for (key in prop.keySet()) {
            val id = Identifier.tryParse(key)

            if (id == null) {
                logger.warn("Invalid map property identifier {}", key)
                continue
            }

            val obj = prop.get(key)

            mapProperties.set(id, obj)
        }
    }

    private fun applyMapEffects() {
        val prop = map.getProperty<Any?>("effects")

        if (prop !is JSONArray) return

        val effects = ApEffects.fromJson(prop, gameHandle.logger)

        enableEffects(effects)
    }

    @Synchronized
    protected fun enableEffects(effects: Iterable<ApEffect>) {
        activeEffects.addAll(effects)

        for (effect in effects) {
            gameHandle.playerUtil.enableEffect(effect)
        }
    }

    @Synchronized
    protected fun disableEffects() {
        for (effect in activeEffects) {
            gameHandle.playerUtil.disableEffect(effect)
        }

        activeEffects.clear()
    }

    private fun resetPlayers() {
        for (player in allPlayers()) {
            gameHandle.playerUtil.resetPlayer(player)
        }
    }

    protected open fun afterInitialDelay() {
        for (player in allPlayers()) {
            sendGo(player)
        }

        go()
    }

    protected fun sendGo(player: ServerPlayer) {
        val text = gameHandle.translations.translateText("ap2.go")
            .formatted(ChatFormatting.RED)
            .translateFor(player)

        Title.get(player).title(text, Component.empty(), 5, 20, 5)

        player.playNotifySound(SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0f)
    }

    private fun registerDefaultHooks() {
        val playerUtil = gameHandle.playerUtil

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, _ ->
            if (!source.isOf(DamageTypes.FELL_OUT_OF_WORLD) || entity !is ServerPlayer) return@registerWith true

            if (entity.isSpectator) {
                gameHandle.worldFacade.teleport(entity)
                false
            } else {
                true
            }
        }

        PlayerSpawnLocationCallback.HOOK.registerWith(hooks) { data ->
            playerUtil.resetPlayer(data.player)
        }

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, _, _, _ ->
            if (player.isCreative || mapProperties.getBoolean(ApMapProperties.ALLOW_BLOCK_INTERACTION, true)) {
                InteractionResult.PASS
            } else {
                InteractionResult.FAIL
            }
        }
    }

    val initialDelay: Int
        get() {
            val players = gameHandle.participants.asSet.size
            return PlayerUtil.getLoadingDelayTicks(players)
        }

    protected fun useSurvivalMode() {
        gameHandle.playerUtil.setDefaultGameMode(GameType.SURVIVAL)
    }

    protected fun useOldCombat() {
        gameHandle.playerUtil.setDefaultCombatStyle(CombatStyles.CLASSIC)
    }

    /**
     * Disables any form of healing. Damage is still allowed.
     */
    protected fun useNoHealing() {
        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            health > entity.health
        }
    }

    protected fun useTaskDisplay(): TranslatedBossBar {
        val gameInfo = gameHandle.gameInfo
        val id = gameInfo.identifier("task")

        val bossBar = gameHandle.translations.translateBossBar(id, gameInfo.taskKey, *gameInfo.taskArguments)
            .with(gameHandle.bossBarProvider)
            .formatted(ChatFormatting.GREEN)

        bossBar.setColor(BossEvent.BossBarColor.GREEN)

        bossBar.addPlayers(PlayerLookup.all(server))

        gameHandle.bossBarHandler.showOnJoin(bossBar)

        return bossBar
    }

    protected fun useTaskTimer(seconds: Int): BossBarTimer {
        val subject = translate(gameHandle.gameInfo.taskKey)

        return commons().createTimer(subject, seconds)
    }

    protected fun usePlayerDynamicTaskDisplay(vararg args: Any?): DynamicTranslatedPlayerBossBar {
        return usePlayerDynamicDisplay(gameHandle.gameInfo.taskKey, *args)
    }

    protected fun usePlayerDynamicDisplay(key: String?, vararg args: Any?): DynamicTranslatedPlayerBossBar {
        val id = ApConstants.identifier("task")

        val translations = gameHandle.translations
        val provider = gameHandle.bossBarProvider

        val bossBar = DynamicTranslatedPlayerBossBar(id, key, args, translations, provider)
            .formatted(ChatFormatting.GREEN)

        bossBar.setColor(BossEvent.BossBarColor.GREEN)
        bossBar.setPercent(1f)

        for (player in gameHandle.participants) {
            bossBar.add(player)
        }

        bossBar.init(gameHandle.hooks)

        return bossBar
    }

    protected fun enableLocatorBar() {
        this.locatorBarEnabled = true
    }

    /**
     * Get or create [GameCommons] for this game.
     * This method should only be called after the map is ready.
     * If the [GameCommons] already need to be accessed during bootstrap, [.commons] should be used instead.
     * @return The [GameCommons] singleton in scope of this game instance.
     */
    fun commons(): GameCommons {
        return commons(this.map, this.level)
    }

    protected fun commons(map: GameMap, world: ServerLevel): GameCommons {
        var currentCommons = commons

        if (currentCommons != null) return currentCommons

        synchronized(this) {
            currentCommons = commons

            if (currentCommons != null) return currentCommons

            commons = GameCommons(gameHandle, map, world)
        }

        return commons!!
    }

    protected fun isParticipating(player: ServerPlayer): Boolean =
        gameHandle.participants.isParticipating(player)

    protected val hooks: HookRegistrar
        get() = gameHandle.hooks

    protected abstract fun prepare()

    protected abstract fun go()
}