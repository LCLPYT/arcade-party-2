package work.lclpnet.ap2.api.game.team;

import net.minecraft.world.scores.TeamColor;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.ColorUtil;

import java.util.Locale;

public enum DyeTeamKey implements TeamKey {

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

    private final int color;
    private final TeamColor teamColor;

    DyeTeamKey(TeamColor color) {
        this.color = color.rgb();
        this.teamColor = color;
    }

    DyeTeamKey(int color) {
        this.color = color;
        this.teamColor = closestTeamColor(color);
    }

    private TeamColor closestTeamColor(int color) {
        double minDist = Double.POSITIVE_INFINITY;
        TeamColor closest = null;

        for (TeamColor formatting : TeamColor.values()) {
            int colorValue = formatting.rgb();

            double dist = ColorUtil.squaredDistance(color, colorValue);

            if (dist < minDist) {
                minDist = dist;
                closest = formatting;
            }
        }

        if (closest == null) {
            throw new IllegalStateException("No matching color found");
        }

        return closest;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public int color() {
        return color;
    }

    @Override
    public TeamColor teamColor() {
        return teamColor;
    }

    public static @Nullable DyeTeamKey byId(String id) {
        try {
            return DyeTeamKey.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
