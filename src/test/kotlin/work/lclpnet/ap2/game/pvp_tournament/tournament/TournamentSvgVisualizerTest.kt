package work.lclpnet.ap2.game.pvp_tournament.tournament

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

class TournamentSvgVisualizerTest {

    companion object {

        var svgPath: Path? = null
        var steveSkin: BufferedImage? = null

        @JvmStatic
        @BeforeAll
        fun setup() {
            svgPath = Files.createTempDirectory("ap2_pvp_tournament")
            steveSkin = ImageIO.read(this::class.java.getResource("/steve.png"))
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun generateSvg(n: Int) {
        val builder = SingleEliminationTournamentBuilder(ByeTracker())
        val players = mutableListOf<PlayerRef>()
        var char = 'A'

        repeat(n) {
            val player = PlayerRef(UUID.randomUUID(), char++.toString())

            players.add(player)
        }

        val tournament = builder.build(players)

        svgPath?.let {
            val outPath = it.resolve("t_$n.svg")

            TournamentSvgVisualizer().generateSvg(tournament, outPath)

            println("Wrote $outPath")
        }
    }
}