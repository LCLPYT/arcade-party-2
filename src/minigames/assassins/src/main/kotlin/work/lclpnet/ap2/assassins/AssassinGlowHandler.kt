package work.lclpnet.ap2.assassins

import net.minecraft.ChatFormatting
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.scores.PlayerTeam
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.kibu.access.entity.EntityAccess
import work.lclpnet.kibu.access.network.packet.TeamS2CPacketAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.network.ServerSendPacketCallback
import work.lclpnet.kibu.hook.util.PendingResult
import java.util.*

/**
 * Lets a player see another player outlined with a colored glow, visible only to that one viewer.
 *
 * Vanilla glow is global and its color is derived from the glowing entity's scoreboard team, which is
 * also global. To make both per-viewer, this:
 *  - places every participant on their own team, so the team color can be overridden per viewer
 *    without affecting other glowing players,
 *  - sets the GLOWING entity flag only in the packets sent to the relevant viewer (mirrors the
 *    per-player invisibility trick in [work.lclpnet.ap2.impl.util.handler.VisibilityHandler]),
 *  - overrides the team color per viewer via [TeamS2CPacketAccess.withColor] (mirrors the
 *    per-player show-friendly-invisibles trick in
 *    [work.lclpnet.ap2.impl.util.handler.VisibilityManager]).
 */
class AssassinGlowHandler(
    private val server: MinecraftServer,
    private val scoreboardManager: CustomScoreboardManager
) {

    private val teams = HashMap<UUID, PlayerTeam>()

    // viewer uuid -> (glowing entity id -> glow color)
    private val glowFor = HashMap<UUID, MutableMap<Int, ChatFormatting>>()

    fun init(hooks: HookRegistrar, participants: Iterable<ServerPlayer>) {
        var index = 0

        for (player in participants) {
            val team = scoreboardManager.createTeam("ap2-assassins-${index++}")
            scoreboardManager.joinTeam(player, team)
            teams[player.uuid] = team
        }

        ServerSendPacketCallback.HOOK.registerWith(hooks, ::overridePacket)
    }

    fun setGlow(viewer: ServerPlayer, target: ServerPlayer, color: ChatFormatting) {
        val team = teams[target.uuid] ?: return

        glowFor.getOrPut(viewer.uuid) { HashMap() }[target.id] = color

        // override the team color for this viewer so the outline renders in the desired color
        val teamPacket = TeamS2CPacketAccess.modifyTeam(
            ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false)
        ) { params -> TeamS2CPacketAccess.withColor(params, color) }
        viewer.connection.send(teamPacket)

        // set the glowing flag for this viewer only
        viewer.connection.send(glowPacket(target, true))
    }

    fun clearGlow(viewer: ServerPlayer, target: ServerPlayer) {
        val map = glowFor[viewer.uuid] ?: return

        if (map.remove(target.id) == null) return

        if (map.isEmpty()) {
            glowFor.remove(viewer.uuid)
        }

        restore(viewer, target)
    }

    fun clearAll() {
        val snapshot = glowFor.entries.map {
            it.key to it.value.keys.toList()
        }

        glowFor.clear()

        for ((viewerId, entityIds) in snapshot) {
            val viewer = server.playerList.getPlayer(viewerId) ?: continue

            for (entityId in entityIds) {
                val target = viewer.level().getEntity(entityId) as? ServerPlayer ?: continue

                restore(viewer, target)
            }
        }
    }

    fun removePlayer(player: ServerPlayer) {
        glowFor.remove(player.uuid)

        val id = player.id

        for ((viewerId, map) in glowFor) {
            if (map.remove(id) == null) continue

            val viewer = server.playerList.getPlayer(viewerId) ?: continue

            restore(viewer, player)
        }
    }

    private fun restore(viewer: ServerPlayer, target: ServerPlayer) {
        val team = teams[target.uuid]

        if (team != null) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false))
        }

        viewer.connection.send(glowPacket(target, false))
    }

    private fun glowPacket(target: ServerPlayer, glowing: Boolean): ClientboundSetEntityDataPacket {
        var flags = target.entityData.get(EntityAccess.FLAGS)

        flags = EntityAccess.setFlag(flags, EntityAccess.GLOWING_FLAG_INDEX, glowing)

        val entry = SynchedEntityData.DataValue.create(EntityAccess.FLAGS, flags)

        return ClientboundSetEntityDataPacket(target.id, listOf(entry))
    }

    private fun overridePacket(packet: Packet<*>, handler: ServerCommonPacketListenerImpl): PendingResult<Packet<*>> {
        // re-apply the glow flag whenever the server re-sends entity data for a glowing target
        if (packet !is ClientboundSetEntityDataPacket || handler !is ServerGamePacketListenerImpl) {
            return PendingResult.pass()
        }

        val glowSet = glowFor[handler.player.uuid] ?: return PendingResult.pass()

        if (packet.id() !in glowSet) return PendingResult.pass()

        val items = packet.packedItems()

        for (i in items.indices) {
            val entry = items[i]

            if (entry.id() != EntityAccess.FLAGS.id()) continue

            val flags = entry.value() as Byte

            val alreadyGlowing = (flags.toInt() and (1 shl EntityAccess.GLOWING_FLAG_INDEX)) != 0

            if (alreadyGlowing) return PendingResult.pass()

            val newFlags = EntityAccess.setFlag(flags, EntityAccess.GLOWING_FLAG_INDEX, true)

            val newItems = ArrayList<SynchedEntityData.DataValue<*>>(items.size)

            for (j in 0 until i) {
                newItems.add(items[j])
            }

            newItems.add(SynchedEntityData.DataValue.create(EntityAccess.FLAGS, newFlags))

            for (j in i + 1 until items.size) {
                newItems.add(items[j])
            }

            val modified: Packet<*> = ClientboundSetEntityDataPacket(packet.id(), newItems)

            return PendingResult.of(modified)
        }

        return PendingResult.pass()
    }
}
