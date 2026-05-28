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
import net.minecraft.world.level.block.Blocks.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.DyeTeamKey.*
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor
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
    private val concrete: Paintable
    private var frozen = false

    init {
        val paintables = ArrayList<Paintable>()

        // wool
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_WOOL
                LIGHT_GRAY -> LIGHT_GRAY_WOOL
                DARK_GRAY -> GRAY_WOOL
                BLACK -> BLACK_WOOL
                BROWN -> BROWN_WOOL
                RED -> RED_WOOL
                BLUE -> BLUE_WOOL
                ORANGE -> ORANGE_WOOL
                YELLOW -> YELLOW_WOOL
                PURPLE -> PURPLE_WOOL
                LIME -> LIME_WOOL
                DARK_GREEN -> GREEN_WOOL
                CYAN -> CYAN_WOOL
                LIGHT_BLUE -> LIGHT_BLUE_WOOL
                MAGENTA -> MAGENTA_WOOL
                PINK -> PINK_WOOL
            }
        }

        // carpet
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_CARPET
                LIGHT_GRAY -> LIGHT_GRAY_CARPET
                DARK_GRAY -> GRAY_CARPET
                BLACK -> BLACK_CARPET
                BROWN -> BROWN_CARPET
                RED -> RED_CARPET
                BLUE -> BLUE_CARPET
                ORANGE -> ORANGE_CARPET
                YELLOW -> YELLOW_CARPET
                PURPLE -> PURPLE_CARPET
                LIME -> LIME_CARPET
                DARK_GREEN -> GREEN_CARPET
                CYAN -> CYAN_CARPET
                LIGHT_BLUE -> LIGHT_BLUE_CARPET
                MAGENTA -> MAGENTA_CARPET
                PINK -> PINK_CARPET
            }
        }

        // concrete
        concrete = Paintable { team ->
            when (team) {
                WHITE -> WHITE_CONCRETE
                LIGHT_GRAY -> LIGHT_GRAY_CONCRETE
                DARK_GRAY -> GRAY_CONCRETE
                BLACK -> BLACK_CONCRETE
                BROWN -> BROWN_CONCRETE
                RED -> RED_CONCRETE
                BLUE -> BLUE_CONCRETE
                ORANGE -> ORANGE_CONCRETE
                YELLOW -> YELLOW_CONCRETE
                PURPLE -> PURPLE_CONCRETE
                LIME -> LIME_CONCRETE
                DARK_GREEN -> GREEN_CONCRETE
                CYAN -> CYAN_CONCRETE
                LIGHT_BLUE -> LIGHT_BLUE_CONCRETE
                MAGENTA -> MAGENTA_CONCRETE
                PINK -> PINK_CONCRETE
            }
        }
        paintables.add(concrete)

        // concrete powder
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_CONCRETE_POWDER
                LIGHT_GRAY -> LIGHT_GRAY_CONCRETE_POWDER
                DARK_GRAY -> GRAY_CONCRETE_POWDER
                BLACK -> BLACK_CONCRETE_POWDER
                BROWN -> BROWN_CONCRETE_POWDER
                RED -> RED_CONCRETE_POWDER
                BLUE -> BLUE_CONCRETE_POWDER
                ORANGE -> ORANGE_CONCRETE_POWDER
                YELLOW -> YELLOW_CONCRETE_POWDER
                PURPLE -> PURPLE_CONCRETE_POWDER
                LIME -> LIME_CONCRETE_POWDER
                DARK_GREEN -> GREEN_CONCRETE_POWDER
                CYAN -> CYAN_CONCRETE_POWDER
                LIGHT_BLUE -> LIGHT_BLUE_CONCRETE_POWDER
                MAGENTA -> MAGENTA_CONCRETE_POWDER
                PINK -> PINK_CONCRETE_POWDER
            }
        }

        // terracotta
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_TERRACOTTA
                LIGHT_GRAY -> LIGHT_GRAY_TERRACOTTA
                DARK_GRAY -> GRAY_TERRACOTTA
                BLACK -> BLACK_TERRACOTTA
                BROWN -> BROWN_TERRACOTTA
                RED -> RED_TERRACOTTA
                BLUE -> BLUE_TERRACOTTA
                ORANGE -> ORANGE_TERRACOTTA
                YELLOW -> YELLOW_TERRACOTTA
                PURPLE -> PURPLE_TERRACOTTA
                LIME -> LIME_TERRACOTTA
                DARK_GREEN -> GREEN_TERRACOTTA
                CYAN -> CYAN_TERRACOTTA
                LIGHT_BLUE -> LIGHT_BLUE_TERRACOTTA
                MAGENTA -> MAGENTA_TERRACOTTA
                PINK -> PINK_TERRACOTTA
            }
        }

        // glazed terracotta
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_GLAZED_TERRACOTTA
                LIGHT_GRAY -> LIGHT_GRAY_GLAZED_TERRACOTTA
                DARK_GRAY -> GRAY_GLAZED_TERRACOTTA
                BLACK -> BLACK_GLAZED_TERRACOTTA
                BROWN -> BROWN_GLAZED_TERRACOTTA
                RED -> RED_GLAZED_TERRACOTTA
                BLUE -> BLUE_GLAZED_TERRACOTTA
                ORANGE -> ORANGE_GLAZED_TERRACOTTA
                YELLOW -> YELLOW_GLAZED_TERRACOTTA
                PURPLE -> PURPLE_GLAZED_TERRACOTTA
                LIME -> LIME_GLAZED_TERRACOTTA
                DARK_GREEN -> GREEN_GLAZED_TERRACOTTA
                CYAN -> CYAN_GLAZED_TERRACOTTA
                LIGHT_BLUE -> LIGHT_BLUE_GLAZED_TERRACOTTA
                MAGENTA -> MAGENTA_GLAZED_TERRACOTTA
                PINK -> PINK_GLAZED_TERRACOTTA
            }
        }

        // stained glass
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_STAINED_GLASS
                LIGHT_GRAY -> LIGHT_GRAY_STAINED_GLASS
                DARK_GRAY -> GRAY_STAINED_GLASS
                BLACK -> BLACK_STAINED_GLASS
                BROWN -> BROWN_STAINED_GLASS
                RED -> RED_STAINED_GLASS
                BLUE -> BLUE_STAINED_GLASS
                ORANGE -> ORANGE_STAINED_GLASS
                YELLOW -> YELLOW_STAINED_GLASS
                PURPLE -> PURPLE_STAINED_GLASS
                LIME -> LIME_STAINED_GLASS
                DARK_GREEN -> GREEN_STAINED_GLASS
                CYAN -> CYAN_STAINED_GLASS
                LIGHT_BLUE -> LIGHT_BLUE_STAINED_GLASS
                MAGENTA -> MAGENTA_STAINED_GLASS
                PINK -> PINK_STAINED_GLASS
            }
        }

        // stained glass pane
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_STAINED_GLASS_PANE
                LIGHT_GRAY -> LIGHT_GRAY_STAINED_GLASS_PANE
                DARK_GRAY -> GRAY_STAINED_GLASS_PANE
                BLACK -> BLACK_STAINED_GLASS_PANE
                BROWN -> BROWN_STAINED_GLASS_PANE
                RED -> RED_STAINED_GLASS_PANE
                BLUE -> BLUE_STAINED_GLASS_PANE
                ORANGE -> ORANGE_STAINED_GLASS_PANE
                YELLOW -> YELLOW_STAINED_GLASS_PANE
                PURPLE -> PURPLE_STAINED_GLASS_PANE
                LIME -> LIME_STAINED_GLASS_PANE
                DARK_GREEN -> GREEN_STAINED_GLASS_PANE
                CYAN -> CYAN_STAINED_GLASS_PANE
                LIGHT_BLUE -> LIGHT_BLUE_STAINED_GLASS_PANE
                MAGENTA -> MAGENTA_STAINED_GLASS_PANE
                PINK -> PINK_STAINED_GLASS_PANE
            }
        }

        // beds
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_BED
                LIGHT_GRAY -> LIGHT_GRAY_BED
                DARK_GRAY -> GRAY_BED
                BLACK -> BLACK_BED
                BROWN -> BROWN_BED
                RED -> RED_BED
                BLUE -> BLUE_BED
                ORANGE -> ORANGE_BED
                YELLOW -> YELLOW_BED
                PURPLE -> PURPLE_BED
                LIME -> LIME_BED
                DARK_GREEN -> GREEN_BED
                CYAN -> CYAN_BED
                LIGHT_BLUE -> LIGHT_BLUE_BED
                MAGENTA -> MAGENTA_BED
                PINK -> PINK_BED
            }
        }

        // shulker boxes
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_SHULKER_BOX
                LIGHT_GRAY -> LIGHT_GRAY_SHULKER_BOX
                DARK_GRAY -> GRAY_SHULKER_BOX
                BLACK -> BLACK_SHULKER_BOX
                BROWN -> BROWN_SHULKER_BOX
                RED -> RED_SHULKER_BOX
                BLUE -> BLUE_SHULKER_BOX
                ORANGE -> ORANGE_SHULKER_BOX
                YELLOW -> YELLOW_SHULKER_BOX
                PURPLE -> PURPLE_SHULKER_BOX
                LIME -> LIME_SHULKER_BOX
                DARK_GREEN -> GREEN_SHULKER_BOX
                CYAN -> CYAN_SHULKER_BOX
                LIGHT_BLUE -> LIGHT_BLUE_SHULKER_BOX
                MAGENTA -> MAGENTA_SHULKER_BOX
                PINK -> PINK_SHULKER_BOX
            }
        }

        // candles
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_CANDLE
                LIGHT_GRAY -> LIGHT_GRAY_CANDLE
                DARK_GRAY -> GRAY_CANDLE
                BLACK -> BLACK_CANDLE
                BROWN -> BROWN_CANDLE
                RED -> RED_CANDLE
                BLUE -> BLUE_CANDLE
                ORANGE -> ORANGE_CANDLE
                YELLOW -> YELLOW_CANDLE
                PURPLE -> PURPLE_CANDLE
                LIME -> LIME_CANDLE
                DARK_GREEN -> GREEN_CANDLE
                CYAN -> CYAN_CANDLE
                LIGHT_BLUE -> LIGHT_BLUE_CANDLE
                MAGENTA -> MAGENTA_CANDLE
                PINK -> PINK_CANDLE
            }
        }

        // candle cake
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_CANDLE_CAKE
                LIGHT_GRAY -> LIGHT_GRAY_CANDLE_CAKE
                DARK_GRAY -> GRAY_CANDLE_CAKE
                BLACK -> BLACK_CANDLE_CAKE
                BROWN -> BROWN_CANDLE_CAKE
                RED -> RED_CANDLE_CAKE
                BLUE -> BLUE_CANDLE_CAKE
                ORANGE -> ORANGE_CANDLE_CAKE
                YELLOW -> YELLOW_CANDLE_CAKE
                PURPLE -> PURPLE_CANDLE_CAKE
                LIME -> LIME_CANDLE_CAKE
                DARK_GREEN -> GREEN_CANDLE_CAKE
                CYAN -> CYAN_CANDLE_CAKE
                LIGHT_BLUE -> LIGHT_BLUE_CANDLE_CAKE
                MAGENTA -> MAGENTA_CANDLE_CAKE
                PINK -> PINK_CANDLE_CAKE
            }
        }

        // banner
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_BANNER
                LIGHT_GRAY -> LIGHT_GRAY_BANNER
                DARK_GRAY -> GRAY_BANNER
                BLACK -> BLACK_BANNER
                BROWN -> BROWN_BANNER
                RED -> RED_BANNER
                BLUE -> BLUE_BANNER
                ORANGE -> ORANGE_BANNER
                YELLOW -> YELLOW_BANNER
                PURPLE -> PURPLE_BANNER
                LIME -> LIME_BANNER
                DARK_GREEN -> GREEN_BANNER
                CYAN -> CYAN_BANNER
                LIGHT_BLUE -> LIGHT_BLUE_BANNER
                MAGENTA -> MAGENTA_BANNER
                PINK -> PINK_BANNER
            }
        }

        // wall banner
        paintables.add { team ->
            when (team) {
                WHITE -> WHITE_WALL_BANNER
                LIGHT_GRAY -> LIGHT_GRAY_WALL_BANNER
                DARK_GRAY -> GRAY_WALL_BANNER
                BLACK -> BLACK_WALL_BANNER
                BROWN -> BROWN_WALL_BANNER
                RED -> RED_WALL_BANNER
                BLUE -> BLUE_WALL_BANNER
                ORANGE -> ORANGE_WALL_BANNER
                YELLOW -> YELLOW_WALL_BANNER
                PURPLE -> PURPLE_WALL_BANNER
                LIME -> LIME_WALL_BANNER
                DARK_GREEN -> GREEN_WALL_BANNER
                CYAN -> CYAN_WALL_BANNER
                LIGHT_BLUE -> LIGHT_BLUE_WALL_BANNER
                MAGENTA -> MAGENTA_WALL_BANNER
                PINK -> PINK_WALL_BANNER
            }
        }

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

    fun getPaintBulletState(team: DyeTeamKey): BlockState = concrete.blockFor(team).defaultBlockState()

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
