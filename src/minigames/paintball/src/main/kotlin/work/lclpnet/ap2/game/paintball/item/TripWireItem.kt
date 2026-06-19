package work.lclpnet.ap2.game.paintball.item

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.paintball.util.PaintManager
import work.lclpnet.ap2.game.paintball.util.PaintballTeam
import work.lclpnet.ap2.game.paintball.util.PaintballTeams
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.ap2.impl.util.ParticleHelper.spawnParticleFor
import work.lclpnet.ap2.impl.util.RayCastUtil.*
import work.lclpnet.ap2.impl.util.SoundHelper.playSound
import work.lclpnet.ap2.impl.util.SoundHelper.playSoundFor
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import java.util.*

private const val MAX_LENGTH = 7.0
private const val TRIPWIRE_MARGIN = 0.01

private const val DISPLAY_LASER_SPACING = 0.125f
private const val DISPLAY_LASER_SIZE = 0.25f
private const val DISPLAY_LASER_TICKS = 4
private const val TRIPWIRE_EXPLOSION_POWER = 3.5f

class TripWireItem(
    private val translations: Translations,
    private val participants: Participants,
    private val world: ServerLevel,
    private val teams: PaintballTeams,
    private val paintManager: PaintManager,
    private val onUsed: (ServerPlayer) -> Unit
) : SpecialItem {

    private val tripwires = HashSet<Tripwire>()

    override fun id() = "tripwire"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.TRIPWIRE_HOOK)

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        val range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)

        val hit = raycast(player, range, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, CollisionContext.empty()) { !it.isSpectator }

        if (hit.type != HitResult.Type.BLOCK || hit !is BlockHitResult) return InteractionResult.PASS

        val pos = hit.location
        val dir = hit.direction.unitVec3

        val opposingHit = raycastBlocks(
            world,
            pos.add(dir.scale(TRIPWIRE_MARGIN)),
            dir,
            MAX_LENGTH,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            CollisionContext.empty()
        )

        if (opposingHit.type != HitResult.Type.BLOCK) {
            translations.translateText("item.tripwire.too_long")
                .withStyle(ChatFormatting.RED)
                .sendTo(player)

            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.2f, 1f)

            return InteractionResult.FAIL
        }

        stack.consume(1, player)

        onUsed(player)

        val length = opposingHit.location.subtract(pos).length()

        tripwires.add(Tripwire(pos, dir, length, player.uuid))

        val activateVolume = 0.45f
        val activatePitch = 1.78f
        val placeVolume = 0.5f
        val placePitch = 1.3f

        val team = teams.teamManager.getTeam(player)

        if (team != null) {
            playSoundFor(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, activateVolume, activatePitch, team.players)
            playSoundFor(SoundEvents.IRON_PLACE, SoundSource.PLAYERS, pos, placeVolume, placePitch, team.players)
        } else {
            playSound(player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, activateVolume, activatePitch)
            playSound(player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, pos, placeVolume, placePitch)
        }

        return InteractionResult.SUCCESS_SERVER
    }

    override fun scheduleTasks(scheduler: TaskScheduler, ctx: SpecialItemContext) {
        scheduler.interval(1, ::tick)
    }

    private fun tick() {
        tripwires.removeIf(Tripwire::tick)
    }

    private inner class Tripwire(
        private val pos: Vec3,
        private val dir: Vec3,
        private val length: Double,
        private val ownerUuid: UUID
    ) {
        private var timer = 0

        fun tick(): Boolean {
            val player = participants.getParticipant(ownerUuid).orElse(null) ?: return true

            val team: Team = teams.teamManager.getTeam(player) ?: return true

            val paintballTeam: PaintballTeam = teams.teamOf(player) ?: return true

            if (timer++ % DISPLAY_LASER_TICKS == 0) {
                showTo(team)
            }

            return checkExplosion(player, paintballTeam)
        }

        private fun showTo(team: Team) {
            var d = 0.0

            while (d <= length) {
                val effect = DustParticleOptions(team.key.color, DISPLAY_LASER_SIZE)

                spawnParticleFor(effect, pos.x + dir.x * d, pos.y + dir.y * d, pos.z + dir.z * d,
                    1, 0.0, 0.0, 0.0, 0.0, team.players)

                d += DISPLAY_LASER_SPACING
            }
        }

        private fun checkExplosion(owner: ServerPlayer, ownerTeam: PaintballTeam): Boolean {
            val hit = raycastEntities(world, pos.add(dir.scale(TRIPWIRE_MARGIN)), dir, length) { entity ->
                entity is ServerPlayer
                    && participants.isParticipating(entity)
                    && teams.teamOf(entity)?.let { it.key != ownerTeam.key } ?: false
            }

            if (hit.type != HitResult.Type.ENTITY || hit !is EntityHitResult) return false

            paintManager.createExplosion(owner, hit.entity.position(), ownerTeam, TRIPWIRE_EXPLOSION_POWER)

            return true
        }
    }
}
