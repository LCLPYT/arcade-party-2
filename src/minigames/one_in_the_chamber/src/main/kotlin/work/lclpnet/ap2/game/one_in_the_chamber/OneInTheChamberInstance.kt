package work.lclpnet.ap2.game.one_in_the_chamber

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.json.JSONArray
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.handler.VisualCooldown
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.*

const val SCORE_LIMIT = 15
const val RESPAWN_SPACING = 20.0

class OneInTheChamberInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle) {

    private val data = IntScoreDataContainer(PlayerRef::create)
    private val random = Random()
    private val respawn = OneInTheChamberSpawns(gameHandle, random)
    private val movementBlocker = SimpleMovementBlocker(gameHandle.rootScheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val respawnCooldown = VisualCooldown(gameHandle.scheduler)

    init {
        useOldCombat()
    }

    override fun getData(): DataContainer<ServerPlayer, PlayerRef> = data

    override fun prepare() {
        commons().gameRuleBuilder()
            .set(GameRules.ENTITY_DROPS, false)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, false)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
            .set(GameRules.FALL_DAMAGE, false)

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
            player.teleportTo(world, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, setOf(), player.yRot, player.xRot, true)
            movementBlocker.disableMovement(player)
        }

        PlayerInventoryHooks.MODIFY_INVENTORY.registerWith(hooks) { event ->
            !event.player().canUseGameMasterBlocks()
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ ->
            projectile.discard()
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, this::onDamage)

        SpectatePlayerCallback.HOOK.registerWith(hooks) { spectator, _ ->
            gameHandle.participants.isParticipating(spectator)
        }

        val scheduler = gameHandle.scheduler

        respawnCooldown.setOnCooldownOver { player ->
            val randomSpawn = respawn.getRandomSpawn()
            player.teleportTo(world, randomSpawn.x + 0.5, randomSpawn.y.toDouble(), randomSpawn.z + 0.5, setOf(), player.yRot, player.xRot, true)
            giveCrossbowToPlayer(player)

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
            giveCrossbowToPlayer(player)
            giveSwordToPlayer(player)
            movementBlocker.enableMovement(player)
        }
    }

    private fun killPlayer(player: ServerPlayer, killer: ServerPlayer?, shot: Boolean) {
        val deathMessages = gameHandle.deathMessages

        val text = when {
            killer != null && shot -> deathMessages.shotBy(player, killer)
            killer != null -> deathMessages.killedBy(player, killer)
            else -> deathMessages.eliminated(player)
        }

        text.formatted(ChatFormatting.GRAY).sendTo(PlayerLookup.all(gameHandle.server))

        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.8f, 0.8f)

        player.setGameMode(GameType.SPECTATOR)
        player.health = 20f

        respawnCooldown.setCooldown(player, 50)
    }

    private fun giveCrossbowToPlayer(player: ServerPlayer) {
        val stack = unbreakable(ItemStack(Items.CROSSBOW))
        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ItemStackTemplate(Items.ARROW)))
        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
            .withStyle { style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD) })

        player.inventory.setItem(1, stack)
    }

    private fun giveSwordToPlayer(player: ServerPlayer) {
        val stack = unbreakable(ItemStack(Items.STONE_SWORD))
        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
            .withStyle { style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD) })

        player.inventory.setItem(0, stack)
        PlayerInventoryAccess.setSelectedSlot(player, 0)
    }

    private fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (entity !is ServerPlayer || winManager.isGameOver) return false

        if (source.directEntity is Projectile) {
            onProjectileDamage(entity, source.directEntity as Projectile)
            return false
        }

        if (entity.hurtTime > 0) return false

        if ((entity.health - amount) <= 0) {
            onLethalDamage(source, entity)
            return false
        }

        return true
    }

    private fun onLethalDamage(source: DamageSource, player: ServerPlayer) {
        val attacker = source.entity
        if (attacker is ServerPlayer && player != attacker) {
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
            giveCrossbowToPlayer(owner)
            return
        }

        killPlayer(player, owner, true)
        onKillGained(owner)
    }

    private fun onKillGained(killer: ServerPlayer) {
        killer.sendOverlayMessage(Component.literal("+1 ").append(TextUtil.getVanillaName(Items.ARROW))
            .withStyle(ChatFormatting.GOLD))

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.CROSSBOW_QUICK_CHARGE_3.value(), SoundSource.PLAYERS, 1f, 1f)

        giveCrossbowToPlayer(killer)
        killer.health = 20f
        data.addScore(killer, 1)

        val newScore = data.getScore(killer)

        if (newScore == SCORE_LIMIT) {
            winManager.complete()
        }

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, 0.8f)
    }
}
