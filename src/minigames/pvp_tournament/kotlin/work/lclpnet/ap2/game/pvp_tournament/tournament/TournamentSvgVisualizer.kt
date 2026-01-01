package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.writeText
import kotlin.math.max

private class VisualNode(
    val match: Match,
    player: PlayerRef? = null,
) {
    var x: Double = 0.0
    var y: Double = 0.0
    val children = mutableListOf<VisualNode>()

    val player: PlayerRef? = when {
        player != null -> player
        match.winner != null -> match.winner
        else -> null
    }

    val isLeaf: Boolean get() = children.isEmpty()
}

fun interface PlayerIcons {
    fun get(player: PlayerRef): BufferedImage
}

class TournamentSvgVisualizer(
    val playerIcons: PlayerIcons,
) {
    fun generateSvg(tournament: Tournament, outPath: Path) {
        val simplifiedTournament = tournament.simplified()
        val finale = simplifiedTournament.finale

        val rootNode = buildVisualTree(finale)

        val rowHeight = 40.0
        val colWidth = 100.0
        val padding = 20.0
        val dotRadius = 4.0

        // Assign Y coordinates (Leaves get fixed rows, parents are averaged)
        val leaves = collectLeaves(rootNode)
        leaves.forEachIndexed { index, node ->
            node.y = padding + index * rowHeight
        }
        assignParentY(rootNode)

        // Assign X coordinates (Leaves at 0, parents based on max child depth)
        assignXCoordinates(rootNode, padding + 100.0, colWidth) // Extra padding for names

        // Determine Canvas Size
        val maxX = getMaxX(rootNode) + padding
        val maxY = leaves.size * rowHeight + padding * 2

        // Generate SVG Content
        val svg = StringBuilder()
        svg.append("""<svg width="$maxX" height="$maxY" xmlns="http://www.w3.org/2000/svg">""")
        svg.append("""<style>
            text { font-family: sans-serif; font-size: 12px; dominant-baseline: middle; }
            image { image-rendering: pixelated; image-rendering: crisp-edges; }
            </style>""".trimIndent())

        // Background
        svg.append("""<rect width="100%" height="100%" fill="white" />""")

        // Recursive Render
        renderNode(svg, rootNode, dotRadius)

        svg.append("</svg>")

        outPath.writeText(svg.toString())
    }

    private fun buildVisualTree(match: Match): VisualNode {
        val node = VisualNode(match = match)

        // Process Left
        if (match.leftChild != null) {
            node.children.add(buildVisualTree(match.leftChild!!))
        } else if (match.leftPlayer != null) {
            node.children.add(VisualNode(match = match, player = match.leftPlayer))
        }

        // Process Right
        if (match.rightChild != null) {
            node.children.add(buildVisualTree(match.rightChild!!))
        } else if (match.rightPlayer != null) {
            node.children.add(VisualNode(match = match, player = match.rightPlayer))
        }

        return node
    }

    private fun collectLeaves(node: VisualNode): List<VisualNode> {
        if (node.isLeaf) return listOf(node)
        return node.children.flatMap { collectLeaves(it) }
    }

    private fun assignParentY(node: VisualNode): Double {
        if (node.isLeaf) return node.y

        val childYs = node.children.map { assignParentY(it) }
        node.y = childYs.average()
        return node.y
    }

    private fun assignXCoordinates(node: VisualNode, startX: Double, colWidth: Double): Double {
        if (node.isLeaf) {
            node.x = startX
            return 0.0
        }

        val maxChildDepth = node.children.maxOf { child ->
            // Recursively calculate depth, but we don't need the return value for positioning child,
            // we need it to determine current node X
            assignXCoordinates(child, startX, colWidth)
        }

        val currentDepth = maxChildDepth + 1

        node.x = startX + (currentDepth * colWidth)

        return currentDepth
    }

    private fun getMaxX(node: VisualNode): Double {
        val childMax = if (node.children.isNotEmpty()) node.children.maxOf { getMaxX(it) } else 0.0

        return max(node.x, childMax)
    }

    private fun renderNode(sb: StringBuilder, node: VisualNode, radius: Double) {
        val strokeColor = "#000"
        val nodeColor = "#000"

        // Render connections to children
        if (node.children.isNotEmpty()) {
            val childYMin = node.children.minOf { it.y }
            val childYMax = node.children.maxOf { it.y }

            // Draw horizontal lines from children to current X
            node.children.forEach { child ->
                sb.appendLine("""<line x1="${child.x}" y1="${child.y}" x2="${node.x}" y2="${child.y}" stroke="$strokeColor" stroke-width="2" />""")

                // Recursively render child
                renderNode(sb, child, radius)
            }

            // Draw Vertical connection line at Node X
            sb.appendLine("""<line x1="${node.x}" y1="$childYMin" x2="${node.x}" y2="$childYMax" stroke="$strokeColor" stroke-width="2" />""")
        }

        // Render Dot for current node
        sb.appendLine("""<circle cx="${node.x}" cy="${node.y}" r="$radius" fill="$nodeColor" />""")

        // Render Players Icon
        val player = when {
            node.isLeaf -> if (node.player != node.match.winner) node.player else null
            node.player != null && node.match.winnerNext?.winner != node.player -> node.player
            else -> null
        }

        if (player != null) {
            val icon = playerIcons.get(player)

            sb.appendLine(getSquareImageSvg(icon, node.x, node.y, 32.0))
        }
    }

    fun getSquareImageSvg(image: BufferedImage, centerX: Double, centerY: Double, size: Double): String {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)

        val encoded = Base64.getEncoder().encodeToString(out.toByteArray())

        return """<image x="${centerX - size / 2}"
                   y="${centerY - size / 2}"
                   width="$size" height="$size"
                   href="data:image/png;base64,$encoded"/>"""
    }
}