package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.math.max

private class VisualNode(
    val match: Match? = null,
    val player: PlayerRef? = null,
) {
    var x: Double = 0.0
    var y: Double = 0.0
    val children = mutableListOf<VisualNode>()

    val isLeaf: Boolean get() = children.isEmpty()
}

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
    svg.append("""<style>text { font-family: sans-serif; font-size: 12px; dominant-baseline: middle; }</style>""")

    // Background
    svg.append("""<rect width="100%" height="100%" fill="white" />""")

    // Recursive Render
    renderNode(svg, rootNode, dotRadius)

    svg.append("</svg>")

    outPath.writeText(svg.toString())
}

// --- Helper Functions ---

private fun buildVisualTree(match: Match): VisualNode {
    val node = VisualNode(match = match)

    // Process Left
    if (match.leftChild != null) {
        node.children.add(buildVisualTree(match.leftChild!!))
    } else if (match.leftPlayer != null) {
        node.children.add(VisualNode(player = match.leftPlayer))
    }

    // Process Right
    if (match.rightChild != null) {
        node.children.add(buildVisualTree(match.rightChild!!))
    } else if (match.rightPlayer != null) {
        node.children.add(VisualNode(player = match.rightPlayer))
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
    val strokeColor = "#333"

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
    val fillColor = if (node.match?.completed == true || node.player != null) "#4CAF50" else "#ccc"
    sb.appendLine("""<circle cx="${node.x}" cy="${node.y}" r="$radius" fill="$fillColor" />""")

    // Render Text for Players (Leaves)
    if (node.isLeaf && node.player != null) {
        // Text anchor end to put it to the left of the point
        sb.appendLine("""<text x="${node.x - 10}" y="${node.y}" text-anchor="end">${node.player.name}</text>""")
    }
}