package work.lclpnet.ap2.game.glowing_bomb.data

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.DisplayEntityAccess
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2

class GbManager(
    private val world: ServerLevel,
    private val map: GameMap,
    private val random: Random,
    private val participants: Participants,
    private val onFull: (GbAnchor) -> Unit
) {
    private val orderedPlayers = mutableListOf<UUID>()
    private val anchors = mutableMapOf<UUID, GbAnchor>()
    private var circleCenter: Vec3? = null
    private var bombHolder: UUID? = null
    private var playerIndex = -1

    fun setupAnchors() {
        val center = MapUtil.readBlockPos(map.requireProperty("circle-center"))
        circleCenter = Vec3.atBottomCenterOf(center)

        val pieces = participants.count()
        val radius = CircleStructureGenerator.calculateRadiusExact(pieces, 2.0)
        val angleStep = Math.PI * 2 / pieces
        val cx = center.x; val cy = center.y; val cz = center.z

        val state = Blocks.RESPAWN_ANCHOR.defaultBlockState()

        var i = 0
        for (player in participants) {
            val angle = angleStep * i++
            val pos = Vec3(
                cx + Math.sin(angle) * radius,
                cy.toDouble(),
                cz + Math.cos(angle) * radius
            )

            val display = Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world)
            display.setPos(pos)
            DisplayEntityAccess.setBlockState(display, state)
            world.addFreshEntity(display)

            val uuid = player.uuid
            anchors[uuid] = GbAnchor(uuid, pos, display)
            orderedPlayers.add(uuid)
        }
    }

    fun teleportPlayers() {
        for (player in participants) {
            teleport(player)
        }
    }

    private fun teleport(player: ServerPlayer) {
        val anchor = anchors[player.uuid] ?: return
        val center = anchor.pos.add(0.5, 0.0, 0.5)
        var dir = center.subtract(circleCenter!!).normalize()

        if (dir.lengthSqr() < 1e-4) {
            dir = when (random.nextInt(4)) {
                0 -> Vec3(1.0, 0.0, 0.0)
                1 -> Vec3(0.0, 0.0, 1.0)
                2 -> Vec3(-1.0, 0.0, 0.0)
                else -> Vec3(0.0, 0.0, -1.0)
            }
        }

        val dx = dir.x(); val dz = dir.z()

        val tx = if (abs(dx) > 1e-4) 1.0 / abs(dx) else Double.POSITIVE_INFINITY
        val tz = if (abs(dz) > 1e-4) 1.0 / abs(dz) else Double.POSITIVE_INFINITY

        val pos = center.add(dir.scale(minOf(tx, tz)))
        val yaw = Math.toDegrees(atan2(dx, -dz)).toFloat()

        player.teleportTo(world, pos.x(), pos.y(), pos.z(), emptySet(), yaw, 0f, true)
    }

    fun assignBomb(): Boolean {
        if (orderedPlayers.isEmpty()) return false

        playerIndex = random.nextInt(orderedPlayers.size)
        val holder = participants.getParticipant(orderedPlayers[playerIndex])

        if (holder.isEmpty) return false

        bombHolder = holder.get().uuid
        return true
    }

    fun bombLocation(): Vec3? {
        val holder = bombHolder ?: return null
        if (!participants.isParticipating(holder)) return null
        return anchors[holder]?.pos?.add(0.5, 1.5, 0.5)
    }

    fun bombAnchor(): GbAnchor? {
        val holder = bombHolder ?: return null
        if (!participants.isParticipating(holder)) return null
        return anchors[holder]
    }

    fun bombHolder(): Optional<ServerPlayer> =
        Optional.ofNullable(bombHolder).flatMap(participants::getParticipant)

    fun addCharge(anchor: GbAnchor) {
        val pos = anchor.pos
        val x = pos.x() + 0.5; val y = pos.y() + 0.5; val z = pos.z() + 0.5
        val charges = anchor.charges

        if (charges >= 4) {
            world.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f)
            return
        }

        anchor.setCharges(charges + 1)
        world.sendParticles(ParticleTypes.WITCH, x, y, z, 30, 0.1, 0.1, 0.1, 0.5)

        val pitch = if (charges < 3) 1.0f else 1.1f
        world.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1f, pitch)

        if (charges == 3) onFull(anchor)
    }

    fun removeAnchor(anchor: GbAnchor) {
        val uuid = anchor.owner
        orderedPlayers.remove(uuid)
        anchors.remove(uuid)
        anchor.discard()
    }

    fun removeAnchorOf(player: ServerPlayer) {
        val uuid = player.uuid
        orderedPlayers.remove(uuid)
        anchors.remove(uuid)?.discard()
    }

    fun hasBomb(player: ServerPlayer): Boolean = player.uuid == bombHolder

    fun nextBombHolder(): ServerPlayer? {
        if (orderedPlayers.isEmpty()) return null

        val nextIndex = Math.floorMod(playerIndex - 1, orderedPlayers.size)
        val uuid = orderedPlayers[nextIndex]
        val holder = participants.getParticipant(uuid)

        if (holder.isEmpty) return null

        bombHolder = uuid
        playerIndex = nextIndex
        return holder.get()
    }
}
