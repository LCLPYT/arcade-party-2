package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.kibu.util.StructureWriter
import work.lclpnet.kibu.util.StructureWriter.Option.*
import work.lclpnet.kibu.util.math.Matrix3i
import java.util.*

class CCBaseManager(
    private val bases: Map<Team, CCBase>,
    private val teamManager: TeamManager
) {

    private fun requireBase(team: Team): CCBase =
        checkNotNull(bases[team]) { "Base not configured for team ${team.key.id}" }

    fun isInAnyBase(x: Double, y: Double, z: Double) =
        bases.values.any { it.isInside(x, y, z) }

    fun isInBase(player: ServerPlayer): Boolean {
        val team = teamManager.getTeam(player) ?: return false

        return requireBase(team).isInside(player.x, player.y, player.z)
    }

    fun getBase(team: Team) = bases[team]

    fun getBases(): Map<Team, CCBase> = bases

    fun getCampfireTeam(pos: BlockPos): Team? =
        bases.entries.firstOrNull { it.value.campfirePos == pos }?.key

    fun getEntityTeam(entity: Entity): Team? =
        bases.entries.firstOrNull { it.value.isEntity(entity) }?.key

    fun openDoors(world: ServerLevel) {
        val opts = EnumSet.of(FORCE_STATE, SKIP_DROPS, SKIP_NEIGHBOUR_UPDATE)

        for (base in bases.values) {
            val struct = base.doorSchematic ?: continue
            val pos = base.doorPos ?: continue

            StructureWriter.placeStructure(
                struct,
                world,
                pos,
                Matrix3i.IDENTITY,
                opts
            )
        }
    }
}
