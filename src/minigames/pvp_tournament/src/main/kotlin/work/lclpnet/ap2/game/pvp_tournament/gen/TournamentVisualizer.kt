package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
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
        if (isSwiss(tournament)) {
            return generateSwissImage(tournament)
        }

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
        if (isSwiss(tournament)) {
            return generateSwissSvg(tournament)
        }

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

    private fun isSwiss(tournament: Tournament): Boolean {
        if (tournament.matches.isEmpty()) return false

        return tournament.matches.all {
            it.leftChild == null && it.rightChild == null
                    && it.winnerNext == null && it.loserNext == null
        }
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

    private fun getImageSvg(image: BufferedImage, centerX: Double, centerY: Double, size: Double, opacity: Double): String {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)

        val encoded = Base64.getEncoder().encodeToString(out.toByteArray())

        return """<image x="${centerX - size / 2}"
                   y="${centerY - size / 2}"
                   width="$size" height="$size"
                   opacity="$opacity"
                   href="data:image/png;base64,$encoded"/>"""
    }

    private data class SwissLayout(
        val currentRound: Int,
        val totalRounds: Int,
        val matches: List<Match>,
        val byes: List<PlayerRef>,
        val width: Double,
        val height: Double,
        val leftIconX: Double,
        val centerX: Double,
        val rightIconX: Double,
        val headerY: Double,
        val firstRowY: Double,
        val rowHeight: Double,
        val iconSize: Double,
        val glyphFontSize: Double,
        val headerFontSize: Double,
    )

    private fun computeSwissLayout(tournament: Tournament): SwissLayout {
        val matches = tournament.matches
        val totalRounds = matches.maxOf { it.round } + 1
        val currentRound = matches.filter { !it.completed }
            .minOfOrNull { it.round } ?: matches.maxOf { it.round }

        val roundMatches = matches
            .filter { it.round == currentRound }
            .sortedBy { it.leftPlayer?.name ?: "" }

        val matchPlayers = roundMatches.flatMap { it.players }.toSet()
        val byes = (tournament.players - matchPlayers).sortedBy { it.name }

        val iconSize = 8.0 * scale
        val gap = 4.0 * scale
        val glyphFontSize = 5.0 * scale
        val headerFontSize = 5.0 * scale
        val rowHeight = iconSize + 2.0 * scale

        val rowWidth = iconSize + gap + glyphFontSize + gap + iconSize
        val width = padding * 2 + rowWidth

        val rows = roundMatches.size + byes.size
        val headerY = padding + headerFontSize / 2
        val firstRowY = padding + headerFontSize + padding + rowHeight / 2
        val height = firstRowY + rows * rowHeight - rowHeight / 2 + padding

        val leftIconX = padding + iconSize / 2
        val centerX = padding + iconSize + gap + glyphFontSize / 2
        val rightIconX = padding + iconSize + gap + glyphFontSize + gap + iconSize / 2

        return SwissLayout(
            currentRound = currentRound,
            totalRounds = totalRounds,
            matches = roundMatches,
            byes = byes,
            width = width,
            height = height,
            leftIconX = leftIconX,
            centerX = centerX,
            rightIconX = rightIconX,
            headerY = headerY,
            firstRowY = firstRowY,
            rowHeight = rowHeight,
            iconSize = iconSize,
            glyphFontSize = glyphFontSize,
            headerFontSize = headerFontSize,
        )
    }

    private suspend fun generateSwissImage(tournament: Tournament): BufferedImage {
        val layout = computeSwissLayout(tournament)

        val image = BufferedImage(layout.width.toInt().coerceAtLeast(1), layout.height.toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, layout.headerFontSize.roundToInt())

        val headerText = "Round ${layout.currentRound + 1} / ${layout.totalRounds}"
        val headerMetrics = g.fontMetrics
        val headerX = (layout.width - headerMetrics.stringWidth(headerText)) / 2
        g.drawString(headerText, headerX.toFloat(), (layout.headerY + headerMetrics.ascent / 2.0 - 1).toFloat())

        layout.matches.forEachIndexed { index, match ->
            val y = layout.firstRowY + index * layout.rowHeight
            renderSwissMatchRow(g, match, layout, y)
        }

        layout.byes.forEachIndexed { index, ref ->
            val y = layout.firstRowY + (layout.matches.size + index) * layout.rowHeight
            val icon = playerIcons.get(ref)
            drawIcon(g, icon, layout.centerX, y, layout.iconSize, 1.0)
        }

        g.dispose()

        return image
    }

    private suspend fun renderSwissMatchRow(g: Graphics2D, match: Match, layout: SwissLayout, y: Double) {
        val left = match.leftPlayer
        val right = match.rightPlayer
        val completed = match.completed
        val draw = match.completedAsDraw

        val leftOpacity: Double = opacityFor(match, left, completed, draw)
        val rightOpacity: Double = opacityFor(match, right, completed, draw)

        if (left != null) {
            drawIcon(g, playerIcons.get(left), layout.leftIconX, y, layout.iconSize, leftOpacity)
        }

        if (right != null) {
            drawIcon(g, playerIcons.get(right), layout.rightIconX, y, layout.iconSize, rightOpacity)
        }

        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, layout.glyphFontSize.roundToInt())

        val metrics = g.fontMetrics
        val glyph = "x"
        val glyphX = layout.centerX - metrics.stringWidth(glyph) / 2.0
        val glyphY = y + metrics.ascent / 2.0 - 1
        g.drawString(glyph, glyphX.toFloat(), glyphY.toFloat())
    }

    private fun opacityFor(match: Match, player: PlayerRef?, completed: Boolean, draw: Boolean): Double {
        if (!completed) return 1.0
        if (draw) return 0.35
        return if (player == match.winner) 1.0 else 0.35
    }

    private fun drawIcon(g: Graphics2D, icon: BufferedImage, centerX: Double, centerY: Double, size: Double, opacity: Double) {
        val previous = g.composite

        if (opacity < 1.0) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity.toFloat())
        }

        val x = (centerX - size / 2).toInt()
        val y = (centerY - size / 2).toInt()
        g.drawImage(icon, x, y, size.toInt(), size.toInt(), null)

        g.composite = previous
    }

    private suspend fun generateSwissSvg(tournament: Tournament): String {
        val layout = computeSwissLayout(tournament)

        val sb = StringBuilder()
        sb.append("""<svg width="${layout.width}" height="${layout.height}" xmlns="http://www.w3.org/2000/svg">""")
        sb.append("""
            <style>
            text { font-family: sans-serif; dominant-baseline: middle; text-anchor: middle; }
            text.header { font-size: ${layout.headerFontSize}px; }
            text.glyph { font-size: ${layout.glyphFontSize}px; }
            image { image-rendering: pixelated; image-rendering: crisp-edges; }
            </style>
            """.trimIndent())

        val headerText = "Round ${layout.currentRound + 1} / ${layout.totalRounds}"
        sb.appendLine("""<text class="header" x="${layout.width / 2}" y="${layout.headerY}" fill="#000">$headerText</text>""")

        layout.matches.forEachIndexed { index, match ->
            val y = layout.firstRowY + index * layout.rowHeight
            renderSwissMatchRowSvg(sb, match, layout, y)
        }

        layout.byes.forEachIndexed { index, ref ->
            val y = layout.firstRowY + (layout.matches.size + index) * layout.rowHeight
            val icon = playerIcons.get(ref)
            sb.appendLine(getImageSvg(icon, layout.centerX, y, layout.iconSize, 1.0))
        }

        sb.append("</svg>")

        return sb.toString()
    }

    private suspend fun renderSwissMatchRowSvg(sb: StringBuilder, match: Match, layout: SwissLayout, y: Double) {
        val left = match.leftPlayer
        val right = match.rightPlayer
        val completed = match.completed
        val draw = match.completedAsDraw

        val leftOpacity = opacityFor(match, left, completed, draw)
        val rightOpacity = opacityFor(match, right, completed, draw)

        if (left != null) {
            sb.appendLine(getImageSvg(playerIcons.get(left), layout.leftIconX, y, layout.iconSize, leftOpacity))
        }

        if (right != null) {
            sb.appendLine(getImageSvg(playerIcons.get(right), layout.rightIconX, y, layout.iconSize, rightOpacity))
        }

        sb.appendLine("""<text class="glyph" x="${layout.centerX}" y="$y" fill="#000">x</text>""")
    }
}