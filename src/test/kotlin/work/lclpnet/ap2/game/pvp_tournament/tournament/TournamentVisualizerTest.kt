package work.lclpnet.ap2.game.pvp_tournament.tournament

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.util.mojang.SkinFetcher
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

class TournamentVisualizerTest {

    companion object {

        var dir: Path? = null
        var defaultIcon: BufferedImage? = null

        @JvmStatic
        @BeforeAll
        fun setup() {
            dir = Files.createTempDirectory("ap2_pvp_tournament")

            defaultIcon = runBlocking {
                requireNotNull(SkinFetcher.loadDefaultSkin())
            }.getSubimage(8, 8, 8, 8)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun base_n(n: Int) {
        val tournament = tournament(n)

        writeSvg(tournament, dir!!.resolve("base_$n.svg"))
        writePng(tournament, dir!!.resolve("base_$n.png"))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun progressed_n(n: Int) {
        val tournament = tournament(n)

        repeat(nextPowerOfTwo(n)) {
            completeLevelRandomly(tournament)
        }

        writeSvg(tournament, dir!!.resolve("progressed_$n.svg"))
        writePng(tournament, dir!!.resolve("progressed_$n.png"))
    }

    private fun completeLevelRandomly(tournament: Tournament) {
        tournament.matches
            .filter { !it.completed }
            .filter { it.players.size == 2 }
            .forEach { it.complete(it.players.random()) }
    }

    private fun writeSvg(
        tournament: Tournament,
        outPath: Path
    ) {
        val svg = TournamentVisualizer {
            // use default skin for every player
            defaultIcon!!
        }.generateSvg(tournament)

        outPath.writeText(svg)

        println("Wrote $outPath")
    }

    private fun writePng(
        tournament: Tournament,
        outPath: Path
    ) {
        val img = TournamentVisualizer {
            // use default skin for every player
            defaultIcon!!
        }.generateImage(tournament)

        outPath.outputStream().use {
            ImageIO.write(img, "png", it)
        }

        println("Wrote $outPath")
    }

    private fun tournament(n: Int): Tournament {
        val builder = SingleEliminationTournamentBuilder(ByeTracker())
        val players = mutableListOf<PlayerRef>()
        var char = 'A'

        repeat(n) {
            val player = PlayerRef(UUID.randomUUID(), char++.toString())

            players.add(player)
        }

        return builder.build(players)
    }

    fun nextPowerOfTwo(n: Int): Int {
        if (n <= 1) return 1
        return 1 shl (32 - Integer.numberOfLeadingZeros(n - 1))
    }
}