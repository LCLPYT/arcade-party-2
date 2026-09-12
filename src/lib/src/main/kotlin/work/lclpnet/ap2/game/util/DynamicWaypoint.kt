package work.lclpnet.ap2.game.util

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAsset
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.Optional
import java.util.UUID

/**
 * A locator bar waypoint that is not backed by an entity.
 *
 * The waypoint position is queried from [position] for each receiving player and may change at any time.
 * When position is `null` the waypoint is hidden from that player.
 * Unlike entity waypoints, the position is transmitted exactly, regardless of the distance to the receiver.
 * The [visibleTo] param decides which players receive the waypoint.
 *
 * Call [update] after the position or the visibility changed.
 */
class DynamicWaypoint(
    private val level: ServerLevel,
    color: Int? = null,
    style: ResourceKey<WaypointStyleAsset> = WaypointStyleAssets.DEFAULT,
    private val visibleTo: (ServerPlayer) -> Boolean = { true },
    private val position: (ServerPlayer) -> Vec3?,
) : WaypointTransmitter {

    private val id: UUID = UUID.randomUUID()
    private val icon = Waypoint.Icon().also {
        it.color = Optional.ofNullable(color)
        it.style = style
    }
    private var tracked = false

    fun track() {
        if (tracked) return

        tracked = true
        level.waypointManager.trackWaypoint(this)
    }

    fun untrack() {
        if (!tracked) return

        tracked = false
        level.waypointManager.untrackWaypoint(this)
    }

    fun update() {
        if (!tracked) return

        level.waypointManager.updateWaypoint(this)
    }

    override fun isTransmittingWaypoint() = true

    override fun waypointIcon(): Waypoint.Icon = icon

    override fun makeWaypointConnectionWith(player: ServerPlayer): Optional<WaypointTransmitter.Connection> {
        if (!visibleTo(player)) return Optional.empty()

        val pos = blockPosition(player) ?: return Optional.empty()

        return Optional.of(Connection(player, pos))
    }

    private fun blockPosition(receiver: ServerPlayer) = position(receiver)?.let(BlockPos::containing)

    private inner class Connection(
        private val receiver: ServerPlayer,
        private var lastPos: BlockPos,
    ) : WaypointTransmitter.Connection {

        override fun connect() {
            receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(id, icon, lastPos))
        }

        override fun disconnect() {
            receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(id))
        }

        override fun update() {
            val pos = blockPosition(receiver) ?: return

            if (pos == lastPos) return

            lastPos = pos

            receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(id, icon, pos))
        }

        override fun isBroken() = !visibleTo(receiver) || blockPosition(receiver) == null
    }
}
