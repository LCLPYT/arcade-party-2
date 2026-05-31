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
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor
import work.lclpnet.ap2.game.team.*
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.TeamRef
import work.lclpnet.ap2.impl.util.world.ExplosionUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import java.util.*
import kotlin.math.max

class PaintManager(
    private val world: ServerLevel,
    private val teams: PaintballTeams,
    private val teamManager: TeamManager,
    private val data: IntScoreDataContainer<Team, TeamRef>,
    private val bounds: BlockShape
) {
    private val paintableBlocks = HashMap<Block, Paintable>()
    private val blockTeamMap = HashMap<Block, DyeTeamKey>()
    private var frozen = false

    init {
        val paintables = ArrayList<Paintable>()

        paintables.add(DyeTeamKey::getWoolBlock)
        paintables.add(DyeTeamKey::getCarpetBlock)
        paintables.add(DyeTeamKey::getConcreteBlock)
        paintables.add(DyeTeamKey::getConcretePowderBlock)
        paintables.add(DyeTeamKey::getTerracottaBlock)
        paintables.add(DyeTeamKey::getGlazedTerracottaBlock)
        paintables.add(DyeTeamKey::getStainedGlassBlock)
        paintables.add(DyeTeamKey::getStainedGlassPaneBlock)
        paintables.add(DyeTeamKey::getBedBlock)
        paintables.add(DyeTeamKey::getShulkerBoxBlock)
        paintables.add(DyeTeamKey::getCandleBlock)
        paintables.add(DyeTeamKey::getCandleCakeBlock)
        paintables.add(DyeTeamKey::getBannerBlock)
        paintables.add(DyeTeamKey::getWallBannerBlock)

        for (paintable in paintables) {
            for (team in DyeTeamKey.entries) {
                paintableBlocks[paintable.blockFor(team)] = paintable
            }

            for (team in teams) {
                blockTeamMap[paintable.blockFor(team.key() as DyeTeamKey)] = team.key() as DyeTeamKey
            }
        }
    }

    fun paintable(block: Block): Paintable? = paintableBlocks[block]

    fun getPaintBulletState(team: DyeTeamKey): BlockState = team.getConcreteBlock().defaultBlockState()

    fun replace(pos: BlockPos, target: DyeTeamKey): Boolean {
        val current = world.getBlockState(pos)
        val paintable = paintable(current.block) ?: return false
        return replace(pos, current, paintable, target)
    }

    fun replace(pos: BlockPos, current: BlockState, paintable: Paintable, targetTeam: DyeTeamKey): Boolean {
        if (frozen) return false

        if (teams.teamBaseAt(pos).map { it.key() != targetTeam }.orElse(false) ?: false) return false

        val baseState = paintable.blockFor(targetTeam).defaultBlockState()
        val targetState = copyProperties(current, baseState)

        if (current == targetState) return false

        val prevTeam = getTeam(current.block)
        if (prevTeam != null) addCount(prevTeam, -1)
        addCount(targetTeam, 1)

        return world.setBlock(
            pos,
            targetState,
            Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS
        )
    }

    fun getTeam(block: Block): DyeTeamKey? = blockTeamMap[block]

    private fun copyProperties(reference: BlockState, state: BlockState): BlockState {
        var result = state

        @Suppress("UNCHECKED_CAST")
        for (property in reference.properties) {
            result = applyProperty(result, reference, property as Property<Comparable<Any>>)
        }

        return result
    }

    private fun <T : Comparable<T>> applyProperty(
        state: BlockState,
        reference: BlockState,
        property: Property<T>
    ): BlockState =
        state.trySetValue(property, reference.getValue(property))

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
            val state = world.getBlockState(pos)

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
            replace(affectedPos, team.key() as DyeTeamKey)
        }

        access.invokeHurtEntities()

        ExplosionUtil.sendExplosion(level, explosion, ParticleTypes.EXPLOSION_EMITTER)
    }

    fun freeze() {
        frozen = true
    }
}
