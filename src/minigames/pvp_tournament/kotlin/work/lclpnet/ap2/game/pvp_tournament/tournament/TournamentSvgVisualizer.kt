package work.lclpnet.ap2.game.pvp_tournament.tournament

import java.nio.file.Path
import kotlin.io.path.writeText

data class Point(val x: Double, val y: Double)

fun line(x1: Double, y1: Double, x2: Double, y2: Double): String {
    return """<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" class="match-line" />"""
}

fun circle(cx: Double, cy: Double, r: Int): String {
    return """<circle cx="$cx" cy="$cy" r="$r" class="point" />"""
}

fun text(x: Double, y: Double, content: String, anchor: String = "middle"): String {
    // Escape XML special chars
    val safeContent = content
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    return """<text x="$x" y="$y" text-anchor="$anchor" class="winner-text">$safeContent</text>"""
}

class TournamentSvgVisualizer {

    fun generateSvg(tournament: Tournament, outPath: Path) {
        // 1. Identify the Root (The Final Match)
        // We assume the root is the match that has no "winnerNext" within this specific tree context.
        // If there are multiple (e.g., disconnected brackets), we pick the first one found or handle list.
        // For this implementation, we visualize the main tree ending at the final.
        val rootMatch = tournament.matches.firstOrNull { it.winnerNext == null }
            ?: throw IllegalArgumentException("No final match found (circular dependency or empty tournament).")

        // 2. Configuration for Layout
        val nodeWidth = 150.0  // Horizontal space per round
        val nodeHeight = 60.0  // Vertical space per player slot
        val padding = 50.0

        // 3. Layout Calculation
        // We need to determine the (x, y) coordinates for every match.
        // Strategy:
        // - X is determined by depth from the root (Root is at max X).
        // - Y is determined by the "leaf" position in a flattened list.

        val layout = HashMap<Match, Point>()

        // Calculate the maximum depth to determine canvas width
        fun getDepth(m: Match): Int {
            if (m.getChildren().isEmpty()) return 0
            return 1 + m.getChildren().maxOf { getDepth(it) }
        }
        val maxDepth = getDepth(rootMatch)
        val totalWidth = (maxDepth + 1) * nodeWidth + (padding * 2)

        // Helper to assign Y coordinates based on leaf traversal
        // Returns the vertical center of the subtree rooted at 'match'
        var currentLeafCounter = 0.0

        fun calculateLayout(match: Match, depth: Int): Point {
            val children = match.getChildren()

            val x = totalWidth - padding - (depth * nodeWidth)
            val y: Double

            if (children.isEmpty()) {
                // It's a leaf node match (first round)
                // Position based on the running counter
                y = padding + (currentLeafCounter * nodeHeight)
                currentLeafCounter++
            } else {
                // It's an internal node
                // Reorder logic: We can swap left/right here if needed to avoid crossing,
                // but in a strict tree, standard DFS ensures no crossing.
                // We ensure we process children recursively first.
                val childPositions = children.map { calculateLayout(it, depth + 1) }

                // The Y of this match is the average Y of its immediate children
                y = childPositions.map { it.y }.average()
            }

            val point = Point(x, y)
            layout[match] = point
            return point
        }

        // Execute layout
        calculateLayout(rootMatch, 0)

        // Calculate total height based on leaves processed
        val totalHeight = (currentLeafCounter * nodeHeight) + (padding * 2)

        // 4. Generate SVG Content
        val sb = StringBuilder()
        sb.append("""<svg width="$totalWidth" height="$totalHeight" xmlns="http://www.w3.org/2000/svg">""")

        // Styles
        sb.append("""
        <style>
            .match-line { stroke: #333; stroke-width: 2; fill: none; }
            .winner-text { font-family: sans-serif; font-size: 12px; text-anchor: middle; dominant-baseline: middle; fill: #000; }
            .player-label { font-family: sans-serif; font-size: 10px; fill: #666; }
            .point { fill: #333; }
        </style>
    """.trimIndent())

        // Helper to draw
        fun drawTree(match: Match) {
            val currentPos = layout[match] ?: return

            // Draw children connectors
            val children = match.getChildren()

            if (children.isNotEmpty()) {
                val childPositions = children.mapNotNull { layout[it] }

                // 1. Horizontal lines from children to current X
                // The prompt says: "horizontal lines originate from the winners of the child match...
                // and continue until the x-coordinate of the current winner."
                // However, visually, we usually stop slightly before to create the fork.
                // Let's follow instructions strictly: Line from Child(x,y) to (Current(x), Child(y))

                for (childPos in childPositions) {
                    sb.appendLine(line(childPos.x, childPos.y, currentPos.x, childPos.y))
                }

                // 2. Vertical line connecting the horizontal lines
                // Connects (Current(x), TopChild(y)) to (Current(x), BottomChild(y))
                val minChildY = childPositions.minOf { it.y }
                val maxChildY = childPositions.maxOf { it.y }
                sb.appendLine(line(currentPos.x, minChildY, currentPos.x, maxChildY))

                // Recurse
                children.forEach { drawTree(it) }
            } else {
                // Leaf match: Draw lines for the raw players feeding into this match
                // This represents the initial state before any match has occurred
                val offset = nodeWidth / 2
                val startX = currentPos.x - offset

                // Upper input (Left Player)
                sb.appendLine(line(startX, currentPos.y - 10, currentPos.x, currentPos.y - 10)) // simple fork
                sb.appendLine(line(startX, currentPos.y + 10, currentPos.x, currentPos.y + 10))
                sb.appendLine(line(currentPos.x, currentPos.y - 10, currentPos.x, currentPos.y + 10))

                // Draw player names for the leaf inputs
                val p1 = match.leftPlayer?.name ?: "TBD"
                val p2 = match.rightPlayer?.name ?: "TBD"
                sb.appendLine(text(startX - 5, currentPos.y - 10, p1, "end"))
                sb.appendLine(text(startX - 5, currentPos.y + 10, p2, "end"))
            }

            // Draw the current winner point/text
            val winnerName = match.winner?.name ?: "?"
            sb.appendLine(circle(currentPos.x, currentPos.y, 3))
            sb.appendLine(text(currentPos.x, currentPos.y - 10, winnerName))
        }

        drawTree(rootMatch)

        sb.append("</svg>")

        outPath.writeText(sb.toString())
    }
}