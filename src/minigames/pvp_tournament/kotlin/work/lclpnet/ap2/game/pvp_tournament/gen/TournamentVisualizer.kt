package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

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

    fun resolvePlayerToShow() = when {
        isLeaf -> if (player != match.winner) player else null
        player != null && match.winnerNext?.winner != player -> player
        else -> null
    }
}

fun interface PlayerIcons {
    suspend fun get(player: PlayerRef): BufferedImage
}

class TournamentVisualizer(
    val playerIcons: PlayerIcons,
    val scale: Int = 1,
) {
    // Shared constants
    private val rowHeight = 10.0 * scale
    private val colWidth = 25.0 * scale
    private val padding = 5.0 * scale
    private val dotRadius = 1.0 * scale

    suspend fun generateImage(tournament: Tournament): BufferedImage {
        val (rootNode, width, height) = calculateLayout(tournament)

        val image = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // background
        g2d.color = Color.WHITE

        renderNodeToGraphics(g2d, rootNode, dotRadius)

        g2d.dispose()

        return image
    }

    suspend fun generateSvg(tournament: Tournament): String {
        val (rootNode, width, height) = calculateLayout(tournament)

        val svg = StringBuilder()
        svg.append("""<svg width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">""")
        svg.append("""
            <style>
            text { font-family: sans-serif; font-size: ${3 * scale}px; dominant-baseline: middle; } 
            image { image-rendering: pixelated; image-rendering: crisp-edges; }
            </style>
            """.trimIndent())

        renderNodeToSvg(svg, rootNode, dotRadius)

        svg.append("</svg>")

        return svg.toString()
    }

    private data class LayoutResult(
        val root: VisualNode,
        val width: Double,
        val height: Double,
    )

    private fun calculateLayout(tournament: Tournament): LayoutResult {
        val finale = tournament.finale
        val rootNode = buildVisualTree(finale)

        // Y Coords
        val leaves = collectLeaves(rootNode)
        leaves.forEachIndexed { index, node -> node.y = padding + index * rowHeight }
        assignParentY(rootNode)

        // X Coords
        assignXCoordinates(rootNode, padding, colWidth)

        // Dimensions
        val maxX = getMaxX(rootNode) + padding
        val maxY = (leaves.size - 1) * rowHeight + padding * 2

        return LayoutResult(rootNode, maxX, maxY)
    }

    private suspend fun renderNodeToGraphics(g: Graphics2D, node: VisualNode, radius: Double) {
        // Render connections
        if (node.children.isNotEmpty()) {
            val childYMin = node.children.minOf { it.y }
            val childYMax = node.children.maxOf { it.y }

            node.children.forEach { child ->
                // Horizontal line from child to parent X
                g.stroke = BasicStroke(max(1.0f, 0.5f * scale))
                g.color = Color.BLACK

                g.draw(Line2D.Double(child.x, child.y, node.x, child.y))

                renderNodeToGraphics(g, child, radius)
            }

            // Vertical line connecting children
            g.stroke = BasicStroke(max(1.0f, 0.5f * scale))
            g.color = Color.BLACK

            g.draw(Line2D.Double(node.x, childYMin, node.x, childYMax))
        }

        // Render Dot
        // fillOval expects top-left corner, so subtract radius
        g.stroke = BasicStroke(max(1.0f, 0.5f * scale))
        g.color = Color.BLACK

        g.fill(Ellipse2D.Double(node.x - radius, node.y - radius, radius * 2, radius * 2))

        // Render Player Icon
        val player = node.resolvePlayerToShow()

        if (player != null) {
            val icon = playerIcons.get(player)
            val iconSize = 8.0 * scale

            // Draw image centered at node.x, node.y
            val xPos = (node.x - iconSize / 2).toInt()
            val yPos = (node.y - iconSize / 2).toInt()

            g.drawImage(icon, xPos, yPos, iconSize.toInt(), iconSize.toInt(), null)
        } else if (node.match.completedAsDraw) {
            val len = 3.0 * scale

            g.stroke = BasicStroke(max(1.0f, 0.5f * scale))
            g.color = Color.BLUE

            g.draw(Line2D.Double(
                node.x - len,
                node.y - len,
                node.x + len,
                node.y + len
            ))

            g.draw(Line2D.Double(
                node.x - len,
                node.y + len,
                node.x + len,
                node.y - len
            ))
        }
    }

    private suspend fun renderNodeToSvg(sb: StringBuilder, node: VisualNode, radius: Double) {
        if (node.children.isNotEmpty()) {
            val childYMin = node.children.minOf { it.y }
            val childYMax = node.children.maxOf { it.y }

            node.children.forEach { child ->
                sb.appendLine("""<line x1="${child.x}" y1="${child.y}" x2="${node.x}" y2="${child.y}" stroke="#000" stroke-width="${max(1.0, 0.5 * scale)}" />""")
                renderNodeToSvg(sb, child, radius)
            }

            sb.appendLine("""<line x1="${node.x}" y1="$childYMin" x2="${node.x}" y2="$childYMax" stroke="#000" stroke-width="${max(1.0, 0.5 * scale)}" />""")
        }

        sb.appendLine("""<circle cx="${node.x}" cy="${node.y}" r="$radius" fill="#000" />""")

        val player = node.resolvePlayerToShow()

        if (player != null) {
            val icon = playerIcons.get(player)

            sb.appendLine(getSquareImageSvg(icon, node.x, node.y, 8.0 * scale))
        } else if (node.match.completedAsDraw) {
            val len = 3.0 * scale

            sb.appendLine("""
                <line 
                x1="${node.x - len}" 
                y1="${node.y - len}" 
                x2="${node.x + len}" 
                y2="${node.y + len}" 
                stroke="#00f" 
                stroke-width="${max(1.0, 0.5 * scale)}" 
                />""".trimIndent())

            sb.appendLine("""
                <line 
                x1="${node.x - len}" 
                y1="${node.y + len}" 
                x2="${node.x + len}" 
                y2="${node.y - len}" 
                stroke="#00f" 
                stroke-width="${max(1.0, 0.5 * scale)}" 
                />""".trimIndent())
        }
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

    private fun collectLeaves(node: VisualNode): List<VisualNode> = when {
        node.isLeaf -> listOf(node)
        else -> node.children.flatMap { collectLeaves(it) }
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