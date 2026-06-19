package work.lclpnet.ap2.game.base

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import org.json.JSONArray
import org.json.JSONObject
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.GameStartSequence
import work.lclpnet.ap2.game.util.configureDefaults
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.TranslationUtil
import work.lclpnet.ap2.impl.util.effect.ApEffect
import work.lclpnet.ap2.impl.util.effect.ApEffects
import work.lclpnet.ap2.impl.util.property.ApMapProperties
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import kotlin.concurrent.Volatile

/** A game instance that:
 * - is played on a map
 * - provides the default onPrepare() and onReady() entry points with the countdown in between
 * - provides common mini-game behaviour configuration methods
 * - configures restrictive protection with bypass for creative operator players
 * - registers default hooks, e.g. for spectators, spawn location and map properties
 *
 * Note that this game instance is not bound be of a specific type, i.e. subclasses can be ob type FFA, TEAM etc. */
abstract class MapGameInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val map: GameMap
) : MiniGameInstance {
    protected val mapProperties: ApMapProperties = ApMapProperties()

    @Volatile
    private var commons: GameCommons? = null
    private val activeEffects: MutableSet<ApEffect> = mutableSetOf()

    override fun start() {
        applyMapEffects()
        loadMapProperties()
        configureDefaults()
        teleportPlayers()
        registerDefaultHooks()
        sendMapCredits()

        prepare()

        val sequence = GameStartSequence(gameHandle)
        configureStartup(sequence)
        sequence.startWithGo { go() }
    }

    /**
     * Hook for subclasses to register [GameStartSequence.beforeGo] phases that run between the initial
     * countdown and the game start. Override and register the phase, then call `super.configureStartup`.
     */
    protected open fun configureStartup(sequence: GameStartSequence) {}

    private fun registerDefaultHooks() {
        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, _, _, _ ->
            if (player.isCreative || mapProperties.getBoolean(ApMapProperties.ALLOW_BLOCK_INTERACTION, true)) {
                InteractionResult.PASS
            } else {
                InteractionResult.FAIL
            }
        }
    }

    protected open fun teleportPlayers() {
        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }
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
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            .sendTo(level.players())
    }

    private fun loadMapProperties() {
        val prop = map.getProperty<Any?>("properties")

        if (prop !is JSONObject) return

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


    /**
     * Disables any form of healing. Damage is still allowed.
     */
    protected fun useNoHealing() {
        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            health > entity.health
        }
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

    protected abstract fun prepare()

    protected abstract fun go()
}