package work.lclpnet.ap2.game.weapon_swap

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.setSelectedSlot
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.createFFAStats
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.util.SubtitleCountdown
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.EntityDamageCallback
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TWO_WEAPONS_THRESHOLD = 8
private const val SPAWN_SPACING_DEFAULT = 8.0
private val MIN_SWAP_DELAY = 7.seconds
private val MAX_SWAP_DELAY = 10.seconds
private val DRAW_DELAY = 3.minutes
private val WARN_BEFORE_END_DELAY = 30.seconds

private val WeaponsReceived = Stat("weapons_received", 0)

class WeaponSwapInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val schema: WeaponSwapSchema,
) : EliminationGameInstance(gameHandle, level, map) {

    private val stats: FFAStatsManager = createFFAStats(winManager, listOf(
        DamageDealt, WeaponsReceived, Kills
    ))
    private val currentHolders = mutableSetOf<UUID>()
    private var previousHolders: Set<UUID> = emptySet()
    private val subtitleCountdown = SubtitleCountdown(gameHandle.server, gameHandle.scheduler, ::swapTimerTick) {
        allPlayers()
    }

    override fun prepare() {
        commons().gameRuleBuilder()
            .set(GameRules.ENTITY_DROPS, false)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, false)
            .set(GameRules.FALL_DAMAGE, false)

        useRemainingPlayersDisplay()
        useSmoothDeath()
        disableTeleportEliminated()

        teleportPlayers()

        PlayerInventoryHooks.MODIFY_INVENTORY.registerWith(gameHandle.hooks) { event ->
            !event.player().canUseGameMasterBlocks()
        }
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                entity is ServerPlayer && source.entity is ServerPlayer
            }
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, _ ->
            if (winManager.gameOver) return@registerWith false

            val victim = entity as? ServerPlayer ?: return@registerWith false
            val attacker = source.entity as? ServerPlayer ?: return@registerWith false

            if (attacker === victim) return@registerWith false
            if (attacker.uuid !in currentHolders) return@registerWith false

            true
        }

        EntityDamageCallback.HOOK.registerWith(hooks) { entity, source, amount ->
            val victim = entity as? ServerPlayer ?: return@registerWith false
            val attacker = source.entity as? ServerPlayer ?: return@registerWith false

            if (attacker !== victim) {
                stats.modify(attacker, DamageDealt) { it + amount.coerceAtMost(victim.health) }
            }

            false
        }

        runAfter(DRAW_DELAY - WARN_BEFORE_END_DELAY) {
            playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 0.5f)

            val subject = gameHandle.translations.translateText("game.ap2.weapon_swap.end")

            createTimer(subject, WARN_BEFORE_END_DELAY).whenDone {
                winManager.complete()
            }
        }

        startCycle()
    }

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        if (attacker is ServerPlayer && attacker !== player && attacker.uuid in currentHolders) {
            gainKill(attacker, stats)
        }

        currentHolders.remove(player.uuid)
        player.inventory.clearContent()
    }

    private fun swapTimerTick(seconds: Int) {
        val pitch = 1f - (seconds - 1) * 0.05f

        playSound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.AMBIENT, 0.5f, pitch)
    }

    override fun teleportPlayers() {
        val scanBox = requireNotNull(schema.scanBox) { "Spawn scan box is not set" }
        val spacing = map.properties.optNumber("spawn-spacing", SPAWN_SPACING_DEFAULT).toDouble()

        teleportToRandomSpawns(scanBox, schema.scanStarts, spacing)
    }

    private fun startCycle() {
        if (winManager.gameOver) return

        val remaining = players().asSet.toList()

        val weaponCount = if (remaining.size >= TWO_WEAPONS_THRESHOLD) 2 else 1

        val eligible = remaining.filter { it.uuid !in previousHolders }
            .ifEmpty { remaining }

        val holders = eligible.shuffled().take(weaponCount).toMutableList()

        if (holders.size < weaponCount) {
            for (p in remaining.shuffled()) {
                if (holders.size >= weaponCount) break
                if (p !in holders) holders.add(p)
            }
        }

        setHolders(holders)

        for (player in remaining) {
            player.addEffect(MobEffectInstance(MobEffects.GLOWING, Ticks.seconds(3), 1, false, false, false))
        }

        val delay = (MIN_SWAP_DELAY..MAX_SWAP_DELAY).random()

        subtitleCountdown.schedule(delay) {
            endCycle()
            startCycle()
        }
    }

    private fun setHolders(newHolders: List<ServerPlayer>) {
        for (p in currentHolders) {
            players().getParticipant(p).ifPresent {
                removeWeaponFrom(it)
            }
        }

        currentHolders.clear()

        for (p in newHolders) {
            giveWeaponTo(p)
            currentHolders.add(p.uuid)
            stats.increment(p, WeaponsReceived)
            ServerPlayerAccess.playSoundToPlayer(p, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.5f)
        }

        translate("game.ap2.weapon_swap.received")
            .formatted(ChatFormatting.AQUA)
            .sendTo(newHolders)
    }

    private fun endCycle() {
        previousHolders = currentHolders.toSet()

        translate("game.ap2.weapon_swap.swap")
            .formatted(ChatFormatting.GREEN)
            .sendTo(allPlayers(), true)
    }

    private fun giveWeaponTo(player: ServerPlayer) {
        val stack = unbreakable(ItemStack(Items.WOODEN_SWORD))

        stack.set(
            DataComponents.ITEM_NAME,
            gameHandle.translations.translateText(player, "game.ap2.weapon_swap.weapon")
                .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) }
        )

        player.inventory.setItem(0, stack)
        player.setSelectedSlot(0)
    }

    private fun removeWeaponFrom(player: ServerPlayer) {
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            if (inv.getItem(i).item === Items.WOODEN_SWORD) {
                inv.setItem(i, ItemStack.EMPTY)
            }
        }
    }
}
