package work.lclpnet.ap2.api.game.team;

import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.ColorUtil;

import java.util.Locale;
import java.util.Optional;

public enum DyeTeamKey implements TeamKey {

    WHITE(Formatting.WHITE),
    LIGHT_GRAY(Formatting.GRAY),
    DARK_GRAY(Formatting.DARK_GRAY),
    BLACK(Formatting.BLACK),
    BROWN(0x603b1f),
    RED(Formatting.RED),
    ORANGE(0xe16100),
    YELLOW(Formatting.YELLOW),
    LIME(Formatting.GREEN),
    DARK_GREEN(Formatting.DARK_GREEN),
    CYAN(0x157788),
    LIGHT_BLUE(0x2389c7),
    BLUE(Formatting.BLUE),
    PURPLE(0x65209d),
    MAGENTA(0xaa31a0),
    PINK(0xd6658f);

    private final int color;
    private final Formatting formatting;

    DyeTeamKey(Formatting formatting) {
        this.color = Optional.ofNullable(formatting.getColorValue()).orElse(0x000000);
        this.formatting = formatting;
    }

    DyeTeamKey(int color) {
        this.color = color;
        this.formatting = closestFormatting(color);
    }

    private Formatting closestFormatting(int color) {
        double minDist = Double.POSITIVE_INFINITY;
        Formatting closest = null;

        for (Formatting formatting : Formatting.values()) {
            Integer colorValue = formatting.getColorValue();

            if (colorValue == null) continue;

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
    public Formatting formatting() {
        return formatting;
    }

    public static @Nullable DyeTeamKey byId(String id) {
        try {
            return DyeTeamKey.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
