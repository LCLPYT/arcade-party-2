package work.lclpnet.ap2.game.pvp_tournament.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.decoration.Mannequin
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.pvp_tournament.gen.Match
import work.lclpnet.gaco.core.api.EntityRef
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*

class MatchInstance(
    val match: Match,
    val arena: ArenaInstance,
    val kit: Kit,
    val level: ServerLevel,
    private val allPlayers: Participants,
) {
    val tasks = mutableListOf<TaskHandle>()
    val npcs = mutableListOf<EntityRef<Mannequin>>()
    val playerUuids = mutableListOf<UUID>()
    var started = false

    val participants: List<Avatar> get() =
        match.players.mapNotNull { entity(it) }

    val players: List<ServerPlayer> get() =
        participants.filterIsInstance<ServerPlayer>()

    fun entity(ref: PlayerRef): Avatar? {
        val player = allPlayers.getParticipant(ref.uuid).orElse(null)

        if (player != null) {
            return if (player.uuid in playerUuids) { player } else null
        }

        return npcs.find { it.uuid == ref.uuid }?.resolve()
    }

    fun teleport(entity: Avatar) {
        val ref = match.players.find { it.uuid == entity.uuid } ?: return
        val spawn = arena.spawns[match.participant(ref)]

        entity.teleport(level, spawn)
    }

    fun ref(entity: Avatar): PlayerRef? = when (entity.uuid) {
        match.leftPlayer?.uuid -> match.leftPlayer
        match.rightPlayer?.uuid -> match.rightPlayer
        else -> null
    }
}