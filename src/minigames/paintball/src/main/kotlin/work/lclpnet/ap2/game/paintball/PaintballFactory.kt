package work.lclpnet.ap2.game.paintball

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.api.util.world.AdjacentBlocks
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.api.util.world.WorldScanner
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.item.SpecialItems
import work.lclpnet.ap2.game.paintball.util.PaintManager
import work.lclpnet.ap2.game.paintball.util.PaintballTeams
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.core.util.ThreadUtil.submitOn
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace
import work.lclpnet.kibu.physics.impl.bullet.collision.space.generator.TerrainGenerator
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread
import java.util.*

private const val BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE

class PaintballFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()
        val teamManager = handle.createTeamManager()

        val random = Random()
        val walls = ResetBlockWorldModifier(level, BLOCK_UPDATE_FLAGS)

        val teams = PaintballTeams(
            teamManager,
            map,
            handle.participants,
            random,
            handle.logger,
            openBases = walls::undo
        )

        teams.setup()

        val bounds = MapUtil.readShape(map, "bounds")
        val paintManager = PaintManager(level, teams, teamManager, bounds)

        val specialItemSpawns = findReachablePositions(level, map, teams)

        replaceTemplateColors(level, teams, paintManager)
        buildMapCollisions(level, bounds)
        closeBases(level, teams, walls)

        return PaintballInstance(
            handle,
            level,
            map,
            teamManager,
            random,
            teams,
            paintManager,
            specialItemSpawns
        )
    }

    private fun findReachablePositions(world: ServerLevel, map: GameMap, teams: PaintballTeams): LongSet {
        val bounds: BlockBox = SpecialItems.getSpawnArea(map).bounds()
        val predicate = BlockPredicate.and(bounds::contains, WalkableBlockPredicate(world))
        val adjacent: AdjacentBlocks = SimpleAdjacentBlocks(predicate, 1)
        val scanner: WorldScanner = BfsWorldScanner(adjacent)

        val spawns: LongSet = LongOpenHashSet()

        val anyTeam = teams.first()
        val startPos = BlockPos.containing(anyTeam.spawn)

        scanner.scan(startPos).forEachRemaining { pos ->
            spawns.add(pos.asLong())
        }

        return spawns
    }

    private fun replaceTemplateColors(world: ServerLevel, teams: PaintballTeams, paintManager: PaintManager) {
        for (team in teams) {
            val color: DyeTeamKey = team.templateColor

            for (pos in team.baseBounds) {
                val state = world.getBlockState(pos)
                val paintable = paintManager.paintable(state.block) ?: continue

                if (!state.isOf(paintable.blockFor(color))) continue

                paintManager.replace(pos, state, paintable, team.key)
            }
        }
    }

    private fun buildMapCollisions(level: ServerLevel, bounds: BlockShape) {
        val space = MinecraftSpace.get(level)
        space.isAutoLoadTerrain = false

        for (pos in bounds) {
            space.chunkCache.loadData(pos.immutable())
        }

        submitOn(PhysicsThread.get(level)) {
            for (pos in bounds) {
                TerrainGenerator.load(space, pos)
            }
        }.join()
    }

    private fun closeBases(level: ServerLevel, teams: PaintballTeams, walls: ResetBlockWorldModifier) {
        for (team in teams) {
            val bounds = team.baseBounds

            for (pos in bounds) {
                if (!bounds.isBorder(pos)) continue

                val state: BlockState = level.getBlockState(pos)

                if (!state.getCollisionShape(level, pos).isEmpty) continue

                walls.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), BLOCK_UPDATE_FLAGS)
            }
        }
    }
}