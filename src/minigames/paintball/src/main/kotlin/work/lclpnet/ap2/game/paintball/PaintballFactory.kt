package work.lclpnet.ap2.game.paintball

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.paintball.util.PaintManager
import work.lclpnet.ap2.game.paintball.util.PaintballTeams
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.core.util.ThreadUtil.submitOn
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

        val bounds = MapUtil.readShape(map, "bounds")
        val paintManager = PaintManager(level, teams, teamManager, bounds)

        replaceTemplateColors(level, teams, paintManager)
        buildMapCollisions(level, bounds)
        closeBases(level, teams, walls)

        return PaintballInstance(handle, level, map, teamManager, random, teams, paintManager)
    }

    private fun replaceTemplateColors(world: ServerLevel, teams: PaintballTeams, paintManager: PaintManager) {
        for (team in teams) {
            val color: DyeTeamKey = team.templateColor

            for (pos in team.baseBounds) {
                val state = world.getBlockState(pos)
                val paintable = paintManager.paintable(state.block) ?: continue

                if (!state.isOf(paintable.blockFor(color))) continue

                paintManager.replace(pos, state, paintable, team.key())
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