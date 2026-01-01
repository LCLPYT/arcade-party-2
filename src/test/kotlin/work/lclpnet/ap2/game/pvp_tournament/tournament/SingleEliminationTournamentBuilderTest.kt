package work.lclpnet.ap2.game.pvp_tournament.tournament

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*
import kotlin.math.max

class SingleEliminationTournamentBuilderTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun build_n_players(n: Int) {
        val builder = SingleEliminationTournamentBuilder(ByeTracker())
        val players = mutableListOf<PlayerRef>()
        var char = 'A'

        repeat(n) {
            val player = PlayerRef(UUID.randomUUID(), char++.toString())

            players.add(player)
        }

        val tournament = builder.build(players)

        assertEquals(max(1, n - 1), tournament.matches.size)
        assertEquals(players.toSet(), tournament.players.toSet())
        assertEquals(1, tournament.matches.count { it.isFinale() })
        assertEquals(max(1, n / 2), tournament.matches.count { it.isLeaf() })

        tournament.matches.filter { it.isLeaf() }.forEach {
            assertTrue(it.leftPlayer != null || it.rightPlayer == null) {
                "Initial matches must have at least one player"
            }
        }

        assertAllMatchesConnectedAsChildren(tournament)
        assertAllMatchesConnectedAsParents(tournament)
    }

    private fun assertAllMatchesConnectedAsParents(tournament: Tournament) {
        val leafs = tournament.matches.filter { it.isLeaf() }.toMutableSet()
        val finale = tournament.finale

        while (leafs.isNotEmpty()) {
            val match = leafs.first()
            leafs.remove(match)

            if (match.winnerNext == null && match.loserNext == null) {
                assertSame(finale, match)
                continue
            }

            match.winnerNext?.let { leafs.add(it) }
            match.loserNext?.let { leafs.add(it) }
        }
    }

    private fun assertAllMatchesConnectedAsChildren(tournament: Tournament) {
        val finale = tournament.finale
        val seen = mutableSetOf(finale)
        val queue = mutableListOf(finale)

        while (queue.isNotEmpty()) {
            val match = queue.removeFirst()

            for (child in match.getChildren()) {
                if (seen.add(child)) {
                    queue.add(child)
                }
            }
        }

        assertEquals(tournament.matches.toSet(), seen)
    }

//    fun generateSvg(tournament: Tournament, outPath: Path) {
//        val iconSize = 32
//        val hSpacing = 80
//        val vSpacing = 30
//
//        fun getIcon(ref: PlayerRef) = requireNotNull(steveSkin)
//
//        fun encode(img: BufferedImage): String {
//            val out = ByteArrayOutputStream()
//            ImageIO.write(img, "png", out)
//            return Base64.getEncoder().encodeToString(out.toByteArray())
//        }
//
//        // depth: leaves = 0
//        fun depth(m: Match, memo: MutableMap<Match, Int>): Int =
//            memo.getOrPut(m) {
//                max(
//                    m.leftChild?.let { depth(it, memo) + 1 } ?: 0,
//                    m.rightChild?.let { depth(it, memo) + 1 } ?: 0
//                )
//            }
//
//        val depthMemo = mutableMapOf<Match, Int>()
//        tournament.matches.forEach { depth(it, depthMemo) }
//
//        val positions = mutableMapOf<Match, Pair<Int, Int>>()
//        var leafIndex = 0
//
//        fun layout(m: Match) {
//            if (positions.containsKey(m)) return
//            val d = depthMemo[m] ?: 0
//            val x = d * (iconSize + hSpacing)
//
//            if (m.leftChild == null && m.rightChild == null) {
//                val y = leafIndex++ * (iconSize + vSpacing)
//                positions[m] = x to y
//            } else {
//                m.leftChild?.let { layout(it) }
//                m.rightChild?.let { layout(it) }
//
//                val ys = listOfNotNull(
//                    m.leftChild?.let { positions[it]?.second },
//                    m.rightChild?.let { positions[it]?.second }
//                )
//                val centerY =
//                    (ys.minOrNull()!! + ys.maxOrNull()!! + iconSize) / 2 - iconSize / 2
//                positions[m] = x to centerY
//            }
//        }
//
//        tournament.matches.forEach { layout(it) }
//
//        val svgWidth =
//            (depthMemo.values.maxOrNull() ?: 0 + 1) * (iconSize + hSpacing)
//        val svgHeight =
//            (positions.values.maxOfOrNull { it.second } ?: 0) + iconSize + vSpacing
//
//        val sb = StringBuilder()
//        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$svgWidth" height="$svgHeight">""")
//        sb.appendLine(
//            """<style>
//        .line { stroke:#000; fill:none; }
//        image { image-rendering: pixelated; image-rendering: crisp-edges; }
//    </style>"""
//        )
//
//        // Draw connections and winner icons
//        positions.forEach { (parent, parentPos) ->
//            val (px, py) = parentPos
//            val parentMidY = py + iconSize / 2
//
//            val childPoints = mutableListOf<Pair<Int, Int>>()
//
//            fun collect(child: Match?) {
//                val pos = child?.let { positions[it] } ?: return
//                val (cx, cy) = pos
//                childPoints += (cx + iconSize) to (cy + iconSize / 2)
//            }
//
//            collect(parent.leftChild)
//            collect(parent.rightChild)
//
//            if (childPoints.isEmpty()) return@forEach
//
//            val midX = childPoints.first().first + hSpacing / 2
//            val junctionY =
//                if (childPoints.size == 1) childPoints.first().second
//                else (childPoints[0].second + childPoints[1].second) / 2
//
//            // child -> junction
//            childPoints.forEach { (sx, sy) ->
//                sb.appendLine(
//                    """<path class="line"
//                   d="M $sx $sy H $midX V $junctionY"/>"""
//                )
//            }
//
//            // junction -> parent (starts here)
//            sb.appendLine(
//                """<path class="line"
//               d="M $midX $junctionY H $px V $parentMidY"/>"""
//            )
//
//            parent.winner?.let {
//                val img = encode(getIcon(it))
//                sb.appendLine(
//                    """<image x="${midX - iconSize / 2}"
//                   y="${junctionY - iconSize / 2}"
//                   width="$iconSize" height="$iconSize"
//                   href="data:image/png;base64,$img"/>"""
//                )
//            }
//        }
//
//        // Draw leaf player icons
//        positions.forEach { (m, pos) ->
//            if (m.leftChild != null || m.rightChild != null) return@forEach
//            val (x, y) = pos
//
//            m.leftPlayer?.let {
//                val img = encode(getIcon(it))
//                sb.appendLine(
//                    """<image x="$x" y="$y"
//                   width="$iconSize" height="$iconSize"
//                   href="data:image/png;base64,$img"/>"""
//                )
//            }
//            m.rightPlayer?.let {
//                val img = encode(getIcon(it))
//                sb.appendLine(
//                    """<image x="$x" y="${y + iconSize + 4}"
//                   width="$iconSize" height="$iconSize"
//                   href="data:image/png;base64,$img"/>"""
//                )
//            }
//        }
//
//        sb.appendLine("</svg>")
//        Files.writeString(outPath, sb.toString())
//    }
}