package work.lclpnet.ap2.game.paintball.util

import it.unimi.dsi.fastutil.Pair
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.resetAttribute
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.VanishManager
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

private const val DEBUG_WALL_CLIMBING = false
private const val HEAL_PER_SECOND = 4.0f
private val HEAL_DELAY_TICKS = Ticks.seconds(3)
private const val SOUND_TICKS = 2
private val DIVE_COOLDOWN = 0.5.seconds

class PaintballTicker(
    private val world: ServerLevel,
    private val participants: Participants,
    private val teams: PaintballTeams,
    private val paintManager: PaintManager,
    private val paintGunManager: PaintGunManager,
    private val vanishManager: VanishManager,
    private val debugController: DebugController
) {
    private val entries = HashMap<UUID, Entry>()

    fun start(scheduler: TaskScheduler, hooks: HookRegistrar) {
        scheduler.interval(::tick, 1)

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, _, _ ->
            if (entity is ServerPlayer && participants.isParticipating(entity)) {
                entry(entity).outOfCombatTicks = 0
            }
            true
        }
    }

    private fun entry(player: ServerPlayer): Entry =
        entries.computeIfAbsent(player.uuid) { Entry() }

    private fun tick() {
        for (player in participants) {
            tickPlayer(player)
        }
    }

    private fun tickPlayer(player: ServerPlayer) {
        val entry = entry(player)
        entry.outOfCombatTicks++

        var inkContactState: BlockState? = null

        if (player.isShiftKeyDown) {
            inkContactState = tickWallClimbing(player)
        }

        val onInk: OnInk
        val resolvedState: BlockState?

        if (inkContactState != null) {
            onInk = OnInk.OWN
            resolvedState = inkContactState
        } else {
            val res = standingOnInk(player)
            onInk = res.left()
            resolvedState = res.right()
        }

        if (onInk != OnInk.ENEMY) {
            player.removeEffect(MobEffects.SLOWNESS)
        }

        if (onInk == OnInk.OWN && player.isShiftKeyDown) {
            if (tickDiving(player, entry, resolvedState)) return
        }
        
        if (entry.diving) {
            onStopDiving(player, entry)
        } else if (entry.diveCooldown >= 1) {
            entry.diveCooldown--
            updateDiveCooldownDisplay(entry, player)
        }

        vanishManager.show(player)
        player.resetAttribute(Attributes.MOVEMENT_SPEED)
        player.resetAttribute(Attributes.SNEAKING_SPEED)
        player.resetAttribute(Attributes.JUMP_STRENGTH)

        paintGunManager.removeReloading(player)

        if (onInk == OnInk.ENEMY) {
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false, false))
        }
    }

    private fun updateDiveCooldownDisplay(entry: Entry, player: ServerPlayer) {
        val cooldownProgress = (entry.diveCooldown.toFloat() / DIVE_COOLDOWN.inWholeTicks).coerceIn(0f..1f)
        player.connection.send(ClientboundSetExperiencePacket(cooldownProgress, 0, 0))
    }

    private fun tickDiving(player: ServerPlayer, entry: Entry, resolvedState: BlockState?): Boolean {
        if (!entry.diving) {
            if (!canDive(entry)) return false

            startDiving(player, entry)
        }

        vanishManager.vanish(player)

        player.setAttribute(Attributes.MOVEMENT_SPEED, 0.14)
        player.setAttribute(Attributes.SNEAKING_SPEED, 1.0)
        player.setAttribute(Attributes.JUMP_STRENGTH, 0.6)

        if (entry.outOfCombatTicks >= HEAL_DELAY_TICKS) {
            player.health += HEAL_PER_SECOND / 20
        }

        if (entry.nextSound-- <= 0) {
            entry.nextSound = SOUND_TICKS
            SoundHelper.playSoundAt(
                player,
                SoundEvents.HONEY_BLOCK_SLIDE,
                SoundSource.PLAYERS,
                0.40f,
                1.65f + Math.random().toFloat() * 0.2f
            )
        }

        teams.teamOf(player)?.let { team ->
            if (resolvedState != null) {
                world.sendParticles(
                    BlockParticleOption(ParticleTypes.BLOCK, resolvedState),
                    player.x, player.y, player.z, 2, 0.2, 0.0, 0.2, 0.2
                )
            } else {
                world.sendParticles(
                    DustParticleOptions(team.key.color, 0.8f),
                    player.x, player.y, player.z, 2, 0.2, 0.0, 0.2, 0.2
                )
            }
        }

        paintGunManager.setReloading(player)
        tickReload(player, entry)

        return true
    }

    private fun canDive(entry: Entry): Boolean = 
        entry.diveCooldown <= 0

    private fun startDiving(player: ServerPlayer, entry: Entry) {
        entry.diving = true

        // cooldown will only start decreasing when stopping dive
        entry.diveCooldown = DIVE_COOLDOWN.inWholeTicks.toInt()

        updateDiveCooldownDisplay(entry, player)

        SoundHelper.playSoundAt(player, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.4f, 1.2f)
    }

    private fun onStopDiving(player: ServerPlayer, entry: Entry) {
        entry.diving = false

        SoundHelper.playSoundAt(player, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.4f, 0.8f)
    }

    private fun tickWallClimbing(player: ServerPlayer): BlockState? {
        val playerTeam = teams.teamOf(player) ?: return null

        val input = PlayerUtil.getHorizontalInputVector(player)

        if (DEBUG_WALL_CLIMBING) {
            debugController.exclusive("input_" + player.scoreboardName) { controller ->
                controller.renderer().ifPresent { renderer ->
                    renderer.arrow(player.position(), input, 0.5, Blocks.REDSTONE_BLOCK.defaultBlockState())
                }
            }
        }

        val dimensions: EntityDimensions = player.getDimensions(player.pose)
        val width = dimensions.width()

        val hit: BlockHitResult = RayCastUtil.raycastBlocks(
            world, player.position(), input, width.toDouble(),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.of(player)
        )

        if (hit.type != HitResult.Type.BLOCK) return null

        val collisionState = world.getBlockState(hit.blockPos)
        val collisionTeam: DyeTeamKey = paintManager.getTeam(collisionState.block) ?: return null

        if (collisionTeam != playerTeam.key) return null

        VelocityModifier.setVelocity(player, player.deltaMovement.with(Direction.Axis.Y, 0.25))

        return collisionState
    }

    private fun tickReload(player: ServerPlayer, entry: Entry) {
        val pair: Pair<PaintGun, ItemStack> = paintGunManager.getPaintGunAndStack(player).orElse(null) ?: return

        val paintGun: PaintGun = pair.left()
        val stack: ItemStack = pair.right()

        if (stack.damageValue <= 0) return

        if (entry.reloadTicks < paintGun.reloadTicks) {
            entry.reloadTicks++
            return
        }

        entry.reloadTicks = 0
        stack.set(DataComponents.DAMAGE, max(0, stack.damageValue - paintGun.reloadAmount))

        player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.2f, 1f)
    }

    private fun standingOnInk(player: ServerPlayer): Pair<OnInk, BlockState?> {
        if (player.isSpectator) return Pair.of(OnInk.NONE, null)

        val team = teams.teamOf(player) ?: return Pair.of(OnInk.NONE, null)

        val width = player.getDimensions(player.pose).width().toDouble()
        var ownInkContactState: BlockState? = null

        val box = BlockBox.of(AABB.ofSize(player.position(), width, 0.1, width))

        for (pos in box) {
            val state = world.getBlockState(pos)
            val paintTeam: DyeTeamKey = paintManager.getTeam(state.block) ?: continue

            if (paintTeam != team.key) {
                return Pair.of(OnInk.ENEMY, state)
            }

            ownInkContactState = state
        }

        return if (ownInkContactState != null) Pair.of(OnInk.OWN, ownInkContactState)
        else Pair.of(OnInk.NONE, null)
    }

    private enum class OnInk { NONE, ENEMY, OWN }

    private class Entry {
        var reloadTicks = 0
        var outOfCombatTicks = 0
        var nextSound = 0
        var diving = false
        var diveCooldown = 0
    }
}
