package work.lclpnet.ap2.game.pvp_tournament.gen

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.util.mojang.SkinFetcher
import work.lclpnet.gaco.asset.cache.SqliteCacheIndex
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

class TournamentVisualizerTest {

    val logger: Logger = LoggerFactory.getLogger(TournamentVisualizerTest::class.java)

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

    @Disabled
    @Test
    fun progressed_withRealPlayers() {
        val players = listOf(
            PlayerRef(UUID.fromString("7357a549-fa3e-4342-91b2-63e5e73ed39a"), "LCLP"),
            PlayerRef(UUID.fromString("1a80d996-c4f0-4ae0-9089-33e8a7ea171b"), "sogoma"),
            PlayerRef(UUID.fromString("4eb6bcf7-023f-4b57-b0c3-716a9dbba51f"), "Secuenix"),
            PlayerRef(UUID.fromString("2c77dc2d-74bb-4d63-8161-7f7d758a7a3e"), "FirebowHD"),
            PlayerRef(UUID.fromString("9e8493e1-ec9a-4141-a360-eb8633558df1"), "SirHuhn"),
            PlayerRef(UUID.fromString("9ea5070f-a30c-4cb3-8e53-dc35a19587da"), "b0ps_"),
            PlayerRef(UUID.fromString("a16bf50d-9e08-4855-826b-5922f47ff451"), "DerBerliner_"),
            PlayerRef(UUID.fromString("303f0f70-8e47-4997-978f-4b4c0f76889c"), "LauchFeuer"),
            PlayerRef(UUID.fromString("3de9c906-6cf2-496d-9b3f-2cebbaa98111"), "TnTheo"),
            PlayerRef(UUID.fromString("81012cc4-97e9-4817-8662-4deacf397902"), "Quadrubo"),
            PlayerRef(UUID.fromString("dd7f6933-5eea-4c2e-8dc2-4a7d54b01565"), "Tiddeles"),
            PlayerRef(UUID.fromString("857b9294-630a-4865-b429-932b6e2858c4"), "2Spielverderber"),
        )

        val tournament = SingleEliminationTournamentBuilder(ByeTracker()).build(players)

        repeat(nextPowerOfTwo(players.size)) {
            completeLevelRandomly(tournament)
        }

        // init jdbc
        Class.forName("org.sqlite.JDBC", true, SqliteCacheIndex::class.java.getClassLoader())

        val skins = runBlocking {
            val skinFetcher = SkinFetcher(
                SkinFetcher.createHttpClient(),
                SkinFetcher.sharedAssetCache(logger),
                SkinFetcher.sharedSkinDirectory(),
                logger
            )

            players.associateWith { player ->
                async { skinFetcher.fetchSkin(player.uuid) }
            }.mapValues { entry ->
                entry.value.await()?.let { SkinFetcher.getFaceTexture(it) }
            }
        }

        writeSvg(tournament, dir!!.resolve("tournament.svg")) {
            requireNotNull(skins[it])
        }

        writePng(tournament, dir!!.resolve("tournament.png")) {
            requireNotNull(skins[it])
        }
    }

    private fun completeLevelRandomly(tournament: Tournament) {
        tournament.matches
            .filter { !it.completed }
            .filter { it.players.size == 2 }
            .forEach { it.complete(it.players.random()) }
    }

    private fun writeSvg(
        tournament: Tournament,
        outPath: Path,
        icons: PlayerIcons = { defaultIcon!! },
    ) {
        val svg = runBlocking { TournamentVisualizer(icons, scale = 4).generateSvg(tournament) }

        outPath.writeText(svg)

        println("Wrote $outPath")
    }

    private fun writePng(
        tournament: Tournament,
        outPath: Path,
        icons: PlayerIcons = { defaultIcon!! },
    ) {
        val img = runBlocking {
            TournamentVisualizer(icons).generateImage(tournament)
        }

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