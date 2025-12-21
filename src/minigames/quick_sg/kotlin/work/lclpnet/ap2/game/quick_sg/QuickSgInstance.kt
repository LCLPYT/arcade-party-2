package work.lclpnet.ap2.game.quick_sg

import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.ap2.players
import work.lclpnet.ap2.teleport
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val DEBUG_SPAWNS = false

class QuickSgInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<QuickSgSchema> = useSchema(QuickSgSchema::class.java)

    override fun prepare() {
        useRemainingPlayersDisplay()
        useSmoothDeath()

        teleportPlayers()
    }

    private fun teleportPlayers() {
        val schema = schemaHolder.get()
        val spacing = map.properties.optNumber("spawn-spacing", 16.0).toDouble()

        val finder = SpawnFinder(spacing, commons().debugController())
        val allSpawns = finder.findSpawns(world, schema.scanBox, schema.scanStart)
        val spacedSpawns = finder.generateSpacedSpawns(allSpawns, players().count(), Random.asJavaRandom())

        var i = 0

        for (player in players()) {
            val spawn = spacedSpawns[i++]
            val yaw = Random.nextFloat() * 360f

            player.teleport(spawn, yaw)
        }

        if (DEBUG_SPAWNS) {
            commons().debugController().renderer().ifPresent {
                for (pos in allSpawns) {
                    it.marker(pos, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), 0x0000ff)
                }
            }
        }
    }

    override fun go() {

    }
}
