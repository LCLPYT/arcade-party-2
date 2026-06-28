package work.lclpnet.ap2.game.team

import net.minecraft.world.scores.TeamColor
import work.lclpnet.ap2.impl.util.ColorUtil

enum class DyeTeamKey : TeamKey {

    WHITE(TeamColor.WHITE),
    LIGHT_GRAY(TeamColor.GRAY),
    DARK_GRAY(TeamColor.DARK_GRAY),
    BLACK(TeamColor.BLACK),
    BROWN(0x603b1f),
    RED(TeamColor.RED),
    ORANGE(0xe16100),
    YELLOW(TeamColor.YELLOW),
    LIME(TeamColor.GREEN),
    DARK_GREEN(TeamColor.DARK_GREEN),
    CYAN(0x157788),
    LIGHT_BLUE(0x2389c7),
    BLUE(TeamColor.BLUE),
    PURPLE(0x65209d),
    MAGENTA(0xaa31a0),
    PINK(0xd6658f);

    override val color: Int
    override val teamColor: TeamColor

    constructor(color: TeamColor) {
        this.color = color.rgb()
        this.teamColor = color
    }

    constructor(color: Int) {
        this.color = color
        this.teamColor = closestTeamColor(color)
    }

    private fun closestTeamColor(color: Int): TeamColor {
        var minDist = Double.POSITIVE_INFINITY
        var closest: TeamColor? = null

        for (formatting in TeamColor.entries) {
            val colorValue = formatting.rgb()

            val dist = ColorUtil.squaredDistance(color, colorValue)

            if (dist < minDist) {
                minDist = dist
                closest = formatting
            }
        }

        return checkNotNull(closest) { "No matching color found" }
    }

    override val id: String = name.lowercase()

    companion object {

        fun byId(id: String): DyeTeamKey? = try {
            valueOf(id.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
