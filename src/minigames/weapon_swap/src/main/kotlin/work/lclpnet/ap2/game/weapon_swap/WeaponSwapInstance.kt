package work.lclpnet.ap2.game.weapon_swap

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.setSelectedSlot
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.ap2.util.SubtitleCountdown
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TWO_WEAPONS_THRESHOLD = 8
private const val SPAWN_SPACING_DEFAULT = 8.0
private val MIN_SWAP_DELAY = 7.seconds
private val MAX_SWAP_DELAY = 10.seconds
private val DRAW_DELAY = 3.minutes
private val WARN_BEFORE_END_DELAY = 30.seconds

private val DAMAGE_DEALT = Stat("damage_dealt", 0f)
private val WEAPONS_RECEIVED = Stat("weapons_received", 0)
private val KILLS = Stat("kills", 0)

class WeaponSwapInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    private val stats: FFAStatsManager = FFAStatsManager(linkedSetOf(DAMAGE_DEALT, WEAPONS_RECEIVED, KILLS))
        .also { winManager.setStatsManager(it) }
    private val currentHolders = mutableSetOf<UUID>()
    private var previousHolders: Set<UUID> = emptySet()
    private val subtitleCountdown = SubtitleCountdown(gameHandle.server, gameHandle.scheduler, ::swapTimerTick) {
        allPlayers()
    }
    private val schemaHolder: SchemaHolder<WeaponSwapSchema> = useSchema(WeaponSwapSchema::class.java)

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

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, amount ->
            if (winManager.isGameOver) return@registerWith false

            val victim = entity as? ServerPlayer ?: return@registerWith false
            val attacker = source.entity as? ServerPlayer ?: return@registerWith false

            if (attacker === victim) return@registerWith false
            if (attacker.uuid !in currentHolders) return@registerWith false

            val applied = amount.coerceAtMost(victim.health)
            stats.modify(attacker, DAMAGE_DEALT) { it + applied }
            true
        }

        runAfter(DRAW_DELAY - WARN_BEFORE_END_DELAY) {
            playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 0.5f)

            val subject = gameHandle.translations.translateText("game.ap2.weapon_swap.end")

            commons().createTimer(subject, WARN_BEFORE_END_DELAY.inWholeSeconds.toInt()).whenDone {
                winManager.complete()
            }
        }

        startCycle()
    }

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        if (attacker is ServerPlayer && attacker !== player && attacker.uuid in currentHolders) {
            stats.increment(attacker, KILLS)
        }

        currentHolders.remove(player.uuid)
        player.inventory.clearContent()
    }

    private fun swapTimerTick(seconds: Int) {
        val pitch = 1f - (seconds - 1) * 0.05f

        playSound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.AMBIENT, 0.5f, pitch)
    }

    private fun teleportPlayers() {
        val schema = schemaHolder.get()

        require(schema.scanStarts.isNotEmpty()) { "No spawn scan start is set" }
        val scanBox = requireNotNull(schema.scanBox) { "Spawn scan box is not set" }
        val spacing = map.properties.optNumber("spawn-spacing", SPAWN_SPACING_DEFAULT).toDouble()

        val finder = SpawnFinder(spacing, commons().debugController())
        val pool = finder.findSpawns(world, scanBox, schema.scanStarts.toSet())
        val spawns = finder.generateSpacedSpawns(pool, players().count(), Random.asJavaRandom())

        var i = 0

        for (player in players()) {
            val pos = spawns[i++]
            val yaw = Random.nextFloat() * 360f
            player.teleport(pos, yaw)
        }
    }

    private fun startCycle() {
        if (winManager.isGameOver) return

        val remaining = players().getAsSet().toList()

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
            stats.increment(p, WEAPONS_RECEIVED)
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
