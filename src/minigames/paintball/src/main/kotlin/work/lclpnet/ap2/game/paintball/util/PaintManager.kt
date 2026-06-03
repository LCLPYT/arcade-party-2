package work.lclpnet.ap2.game.paintball.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.level.SimpleExplosionDamageCalculator
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor
import work.lclpnet.ap2.game.team.DyeBlockManager
import work.lclpnet.ap2.game.team.Paintable
import work.lclpnet.ap2.game.team.getConcreteBlock
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.TeamRef
import work.lclpnet.ap2.impl.util.world.ExplosionUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import java.util.*
import kotlin.math.max

class PaintManager(
    private val leve: ServerLevel,
    private val teams: PaintballTeams,
    private val teamManager: TeamManager,
    private val data: IntScoreDataContainer<Team, TeamRef>,
    private val bounds: BlockShape
) {
    private val dyeManager = DyeBlockManager(leve)
    private var frozen = false

    init {
        dyeManager.init(teams.map { it.key() })
    }

    fun getPaintBulletState(team: DyeTeamKey): BlockState = team.getConcreteBlock().defaultBlockState()

    fun replace(pos: BlockPos, target: DyeTeamKey): Boolean {
        val current = leve.getBlockState(pos)
        val paintable = dyeManager.paintable(current.block) ?: return false
        return replace(pos, current, paintable, target)
    }

    fun replace(pos: BlockPos, current: BlockState, paintable: Paintable, targetTeam: DyeTeamKey): Boolean {
        if (frozen) return false

        if (teams.teamBaseAt(pos).map { it.key() != targetTeam }.orElse(false) ?: false) return false

        if (!dyeManager.replace(pos, current, paintable, targetTeam)) return false

        val prevTeam = getTeam(current.block)
        if (prevTeam != null) addCount(prevTeam, -1)
        addCount(targetTeam, 1)

        return true
    }

    fun paintable(block: Block): Paintable? = dyeManager.paintable(block)

    fun getTeam(block: Block): DyeTeamKey? = dyeManager.getTeam(block)

    private fun addCount(key: DyeTeamKey, amount: Int) {
        val team = teamManager.getTeam(key).orElse(null) ?: return

        synchronized(this) {
            val score = data.getScore(team)
            data.setScore(team, max(0, score + amount))
        }
    }

    fun countBlocks() {
        val count = Object2IntOpenHashMap<DyeTeamKey>()

        for (value in DyeTeamKey.entries) {
            count.put(value, 0)
        }

        for (pos in bounds) {
            val state = leve.getBlockState(pos)

            val key = getTeam(state.block) ?: continue

            count.computeInt(key) { _, prev -> prev + 1 }
        }

        for (entry in count.object2IntEntrySet()) {
            teamManager.getTeam(entry.key).ifPresent { team ->
                synchronized(this) {
                    data.setScore(team, entry.intValue)
                }
            }
        }
    }

    fun createExplosion(player: ServerPlayer, pos: Vec3, team: PaintballTeam, power: Float) {
        val behavior = SimpleExplosionDamageCalculator(true, true, Optional.empty(), Optional.empty())
        val level = player.level()

        val explosion = ServerExplosion(
            level,
            player,
            null,
            behavior,
            pos,
            power,
            false,
            Explosion.BlockInteraction.KEEP
        )

        level.gameEvent(null, GameEvent.EXPLODE, pos)

        @Suppress("KotlinConstantConditions")
        val access = explosion as ServerExplosionAccessor

        for (affectedPos in access.invokeCalculateExplodedPositions()) {
            replace(affectedPos, team.key())
        }

        access.invokeHurtEntities()

        ExplosionUtil.sendExplosion(level, explosion, ParticleTypes.EXPLOSION_EMITTER)
    }

    fun freeze() {
        frozen = true
    }
}
