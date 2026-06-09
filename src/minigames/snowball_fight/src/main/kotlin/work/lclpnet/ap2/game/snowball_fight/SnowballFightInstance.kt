package work.lclpnet.ap2.game.snowball_fight

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.math.abs

private val WORLD_BORDER_DELAY = Ticks.minutes(1)
private val WORLD_BORDER_TIME = Ticks.minutes(1) + Ticks.seconds(20)
private val COMBAT_IDLE_TICKS = Ticks.seconds(9)
private val FREEZING_DURATION_TICKS = Ticks.seconds(5)
private const val MAX_SNOWBALL_STACKS = 9
private const val SNOWBALL_DAMAGE = 0.75f

class SnowballFightInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map) {

    init {
        useSurvivalMode()
        useOldCombat()
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        useNoHealing()
        useSmoothDeath()
        commons().displayHealth()
    }

    override fun go() {
        val participants = gameHandle.participants

        gameHandle.protect { config ->
            ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
                if (entity is ServerPlayer && participants.isParticipating(entity) && !winManager.isGameOver) {
                    onBreakBlock(entity, pos)
                }
                false
            }

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, damageSource ->
                damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
                    || damageSource.isOf(DamageTypes.FREEZE)
                    || (entity is ServerPlayer && participants.isParticipating(entity)
                    && damageSource.directEntity is Projectile && damageSource.entity !== entity)
            }
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, amount ->
            if (source.directEntity !is Snowball || abs(amount) >= 1e-4f) return@registerWith true
            val world = entity.level() as? ServerLevel ?: return@registerWith true

            entity.hurtServer(world, source, SNOWBALL_DAMAGE)
            false
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            val stack = player.getItemInHand(hand)
            if (stack.isOf(Items.SNOWBALL) && stack.count == 1) {
                onDepleteStack(player)
            }
            InteractionResult.PASS
        }

        for (player in participants) {
            player.setAttribute(Attributes.BLOCK_BREAK_SPEED, 100.0)
        }

        commons().scheduleWorldBorderShrink(
            WORLD_BORDER_DELAY.toLong(),
            WORLD_BORDER_TIME.toLong(),
            Ticks.seconds(5).toLong()
        )

        FreezingManager(
            gameHandle.scheduler,
            gameHandle.translations,
            participants,
            COMBAT_IDLE_TICKS,
            FREEZING_DURATION_TICKS
        ).enable(hooks)
    }

    override fun eliminate(player: ServerPlayer, source: DamageSource?) {
        if (source != null) {
            val world = level
            world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.5f, 1f)

            val x = player.x
            val y = player.y + 1
            val z = player.z

            val effect = BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState())
            world.sendParticles(effect, x, y, z, 50, 0.2, 1.0, 0.2, 1.0)
            world.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 50, 0.2, 1.0, 0.2, 0.05)
        }

        super.eliminate(player, source)
    }

    private fun onDepleteStack(player: Player) {
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        val size = inventory.containerSize

        for (i in 0 until size) {
            if (i == selected) continue
            val stack = inventory.getItem(i)
            if (!stack.isOf(Items.SNOWBALL)) continue

            inventory.setItem(i, ItemStack.EMPTY)
            stack.grow(1)
            inventory.setItem(selected, stack)
            break
        }
    }

    override fun teleportPlayers() {
        val random = Random()

        val spacingValue = map.getProperty<Number?>("spawn-spacing")
        val spacing = spacingValue?.toDouble() ?: 16.0

        val spawns = SpawnFinder(spacing, commons().debugController())
        val available = spawns.findSpawns(level, map)
        val spacedSpawns = spawns.generateSpacedSpawns(available, players().count(), random)

        var i = 0
        for (player in players()) {
            val spawn = spacedSpawns[i++]
            val yaw = random.nextFloat(360f) - 180f

            player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), emptySet(), yaw, 0f, true)
        }
    }

    private fun onBreakBlock(player: ServerPlayer, pos: BlockPos) {
        val state = player.level().getBlockState(pos)

        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            addSnowball(player)
        }
    }

    private fun addSnowball(player: ServerPlayer) {
        if (player.inventory.countItem(Items.SNOWBALL) < MAX_SNOWBALL_STACKS * Items.SNOWBALL.defaultMaxStackSize) {
            player.inventory.add(ItemStack(Items.SNOWBALL, 1))
            return
        }

        translate("game.ap2.snowball_fight.max_snowballs")
            .formatted(ChatFormatting.RED)
            .sendTo(player, true)
    }
}
