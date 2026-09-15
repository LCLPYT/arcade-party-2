package work.lclpnet.ap2.game.one_in_the_chamber

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.json.JSONArray
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.impl.util.DeathMessages
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.handler.VisualCooldown
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.useGameRules
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.*

const val SCORE_LIMIT = 15
const val RESPAWN_SPACING = 20.0
private const val SWORD_SLOT = 0
private const val WEAPON_SLOT = 1
private const val ARROW_SLOT = 7
private const val WEAPON_TOGGLE_SLOT = 8
private const val WEAPON_TOGGLE_COOLDOWN = 5

private val ArrowsShot = Stat("arrows_shot", 0)
private val ArrowsHit = Stat("arrows_hit", 0)
private val Killstreak = Stat("killstreak", 0)

enum class WeaponType(val item: Item) {
    CrossBow(Items.CROSSBOW),
    Bow(Items.BOW);

    fun opposite() = if (this == CrossBow) Bow else CrossBow
}

class OneInTheChamberInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    override val data = useDataContainer(::IntScoreDataContainer)
    private val random = Random()
    private val respawn = OneInTheChamberSpawns(gameHandle, random)
    private val movementBlocker = SimpleMovementBlocker(gameHandle.rootScheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val respawnCooldown = VisualCooldown(gameHandle.scheduler)
    private val stats = useFFAStats(winManager, data, CommonStats.IntScore, listOf(
        DamageDealt, Deaths, ArrowsShot, ArrowsHit, Killstreak
    ))
    private val currentKillstreak = HashMap<UUID, Int>()
    private val playerWeaponTypes = HashMap<UUID, WeaponType>()

    init {
        useOldCombat()
    }

    override fun prepare() {
        useGameRules {
            set(GameRules.ENTITY_DROPS, false)
            set(GameRules.NATURAL_HEALTH_REGENERATION, false)
            set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
            set(GameRules.FALL_DAMAGE, false)
        }

        val spawnsJson: JSONArray = map.requireProperty("random-spawns")
        respawn.loadSpawnPoints(spawnsJson)

        val hooks = gameHandle.hooks
        movementBlocker.init(hooks)
        respawnCooldown.init(hooks)

        useTaskDisplay()

        val scoreboardManager = gameHandle.scoreboardManager
        val objective = scoreboardManager.createObjective("kills", ObjectiveCriteria.DUMMY,
            Component.literal("Kills").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), ObjectiveCriteria.RenderType.INTEGER)

        useScoreboardStatsSync(data, objective)
        scoreboardManager.setDisplay(DisplaySlot.SIDEBAR, objective)

        for (player in gameHandle.participants) {
            val pos = respawn.getRandomSpawn()
            player.teleportTo(level, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, setOf(), player.yRot, player.xRot, true)
            movementBlocker.disableMovement(player)
        }

        PlayerInventoryHooks.MODIFY_INVENTORY.registerWith(hooks) { event ->
            !event.player().canUseGameMasterBlocks()
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ ->
            projectile.discard()
        }

        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, _ ->
            if (shooter is ServerPlayer && gameHandle.participants.isParticipating(shooter)) {
                stats.increment(shooter, ArrowsShot)
            }
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            onUseItem(player, hand)
        }

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, _, hand, _ ->
            onUseItem(player, hand)
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, this::onDamage)

        SpectatePlayerCallback.HOOK.registerWith(hooks) { spectator, _ ->
            gameHandle.participants.isParticipating(spectator)
        }

        val scheduler = gameHandle.scheduler

        respawnCooldown.setOnCooldownOver { player ->
            val randomSpawn = respawn.getRandomSpawn()
            player.teleportTo(level, randomSpawn.x + 0.5, randomSpawn.y.toDouble(), randomSpawn.z + 0.5, setOf(), player.yRot, player.xRot, true)
            giveWeaponToPlayer(player, true)
            giveWeaponToggleItem(player)

            player.abilities.flyingSpeed = 0f
            player.onUpdateAbilities()

            scheduler.immediate(Runnable {
                player.abilities.flyingSpeed = 0.05f
                player.onUpdateAbilities()
                player.setGameMode(gameHandle.playerUtil.defaultGameMode)
            })
        }
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, damageSource ->
                entity is ServerPlayer &&
                    (damageSource.isOf(DamageTypes.ARROW) || damageSource.isOf(DamageTypes.PLAYER_ATTACK))
            }
            ProtectionTypes.MOUNT.allow(config)
        }

        for (player in gameHandle.participants) {
            giveWeaponToPlayer(player, true)
            giveWeaponToggleItem(player)
            giveSwordToPlayer(player)
            movementBlocker.enableMovement(player)
        }
    }

    private fun killPlayer(player: ServerPlayer, killer: ServerPlayer?, shot: Boolean) {
        stats.increment(player, Deaths)
        currentKillstreak[player.uuid] = 0

        if (killer != null && shot) {
            stats.increment(player, ArrowsHit)
        }

        val deathMessages = gameHandle.deathMessages

        val text = when {
            killer != null && shot -> deathMessages.root(
                DeathMessages.SHOT_BY,
                deathMessages.wrap(player),
                deathMessages.killerWithHealth(killer)
            )
            killer != null -> deathMessages.root(
                DeathMessages.KILLED_BY,
                deathMessages.wrap(player),
                deathMessages.killerWithHealth(killer)
            )
            else -> deathMessages.eliminated(player)
        }

        text.withStyle(ChatFormatting.GRAY).sendTo(PlayerLookup.all(gameHandle.server))

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.8f, 0.8f)

        player.setGameMode(GameType.SPECTATOR)
        player.health = 20f

        respawnCooldown.setCooldown(player, 50)
    }

    private fun weaponTypeOf(player: ServerPlayer) = playerWeaponTypes[player.uuid] ?: WeaponType.CrossBow

    private fun giveWeaponToPlayer(player: ServerPlayer, loaded: Boolean) {
        val weaponType = weaponTypeOf(player)
        val stack = ItemStack(weaponType.item).unbreakable()

        if (weaponType == WeaponType.CrossBow && loaded) {
            stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ItemStackTemplate(Items.ARROW)))
        }

        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
            .withStyle { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })

        player.inventory.setItem(WEAPON_SLOT, stack)

        val arrow = if (weaponType == WeaponType.Bow && loaded) ItemStack(Items.ARROW) else ItemStack.EMPTY
        player.inventory.setItem(ARROW_SLOT, arrow)
    }

    private fun giveWeaponToggleItem(player: ServerPlayer) {
        val stack = ItemStack(Items.COMMAND_BLOCK_MINECART)

        val weaponName = TextUtil.getVanillaName(weaponTypeOf(player).item).withStyle(ChatFormatting.YELLOW)

        stack.set(DataComponents.CUSTOM_NAME, gameHandle.translations.translateText(player, "weapon", weaponName)
            .withStyle { it.withItalic(false).applyFormat(ChatFormatting.AQUA) })

        player.inventory.setItem(WEAPON_TOGGLE_SLOT, stack)
    }

    private fun onUseItem(player: Player, hand: InteractionHand): InteractionResult {
        if (player !is ServerPlayer || !gameHandle.participants.isParticipating(player)) return InteractionResult.PASS

        val stack = player.getItemInHand(hand)

        if (!stack.isOf(Items.COMMAND_BLOCK_MINECART)) return InteractionResult.PASS

        if (!player.cooldowns.isOnCooldown(stack)) {
            toggleWeapon(player)
            player.cooldowns.addCooldown(stack, WEAPON_TOGGLE_COOLDOWN)
        }

        return InteractionResult.SUCCESS_SERVER
    }

    private fun toggleWeapon(player: ServerPlayer) {
        val loaded = hasArrow(player)

        playerWeaponTypes[player.uuid] = weaponTypeOf(player).opposite()

        giveWeaponToPlayer(player, loaded)
        giveWeaponToggleItem(player)

        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.4f)
    }

    private fun hasArrow(player: ServerPlayer): Boolean {
        val stack = player.inventory.getItem(WEAPON_SLOT)

        return when {
            stack.isOf(Items.CROSSBOW) -> stack.get(DataComponents.CHARGED_PROJECTILES)?.contains(Items.ARROW) == true
            stack.isOf(Items.BOW) -> player.inventory.getItem(ARROW_SLOT).isOf(Items.ARROW)
            else -> false
        }
    }

    private fun giveSwordToPlayer(player: ServerPlayer) {
        val stack = unbreakable(ItemStack(Items.STONE_SWORD))
        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
            .withStyle { style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD) })

        player.inventory.setItem(SWORD_SLOT, stack)
        PlayerInventoryAccess.setSelectedSlot(player, SWORD_SLOT)
    }

    private fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (entity !is ServerPlayer || winManager.gameOver) return false

        if (source.directEntity is Projectile) {
            onProjectileDamage(entity, source.directEntity as Projectile)
            return false
        }

        if (entity.hurtTime > 0) return false

        if ((entity.health - amount) <= 0) {
            onLethalDamage(source, entity)
            return false
        }

        val attacker = source.entity as? ServerPlayer

        if (attacker != null && attacker != entity) {
            stats.modify(attacker, DamageDealt) {
                it + amount.coerceAtMost(entity.health)
            }
        }

        return true
    }

    private fun onLethalDamage(source: DamageSource, player: ServerPlayer) {
        val attacker = source.entity
        if (attacker is ServerPlayer && player != attacker) {
            stats.modify(attacker, DamageDealt) {
                it + player.health
            }
            killPlayer(player, attacker, false)
            onKillGained(attacker)
        } else {
            killPlayer(player, null, false)
        }
    }

    private fun onProjectileDamage(player: ServerPlayer, projectile: Projectile) {
        projectile.discard()

        val owner = projectile.owner as? ServerPlayer ?: return

        if (owner == player) {
            giveWeaponToPlayer(owner, true)
            return
        }

        stats.modify(owner, DamageDealt) { it + player.health }

        killPlayer(player, owner, true)
        onKillGained(owner)
    }

    private fun onKillGained(killer: ServerPlayer) {
        killer.sendOverlayMessage(Component.literal("+1 ").append(TextUtil.getVanillaName(Items.ARROW))
            .withStyle(ChatFormatting.GOLD))

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.CROSSBOW_QUICK_CHARGE_3.value(), SoundSource.PLAYERS, 1f, 1f)

        val streak = (currentKillstreak[killer.uuid] ?: 0) + 1
        currentKillstreak[killer.uuid] = streak
        stats.modify(killer, Killstreak) { maxOf(it, streak) }

        giveWeaponToPlayer(killer, true)
        killer.health = 20f
        data.addScore(killer, 1)

        val newScore = data.getScore(killer)

        if (newScore == SCORE_LIMIT) {
            winManager.complete()
        }

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, 0.8f)
    }
}
