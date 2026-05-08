package work.lclpnet.ap2.api.game.team;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.ColorUtil;

import java.util.Locale;
import java.util.Optional;

public enum DyeTeamKey implements TeamKey {

    WHITE(ChatFormatting.WHITE),
    LIGHT_GRAY(ChatFormatting.GRAY),
    DARK_GRAY(ChatFormatting.DARK_GRAY),
    BLACK(ChatFormatting.BLACK),
    BROWN(0x603b1f),
    RED(ChatFormatting.RED),
    ORANGE(0xe16100),
    YELLOW(ChatFormatting.YELLOW),
    LIME(ChatFormatting.GREEN),
    DARK_GREEN(ChatFormatting.DARK_GREEN),
    CYAN(0x157788),
    LIGHT_BLUE(0x2389c7),
    BLUE(ChatFormatting.BLUE),
    PURPLE(0x65209d),
    MAGENTA(0xaa31a0),
    PINK(0xd6658f);

    private final int color;
    private final ChatFormatting formatting;

    DyeTeamKey(ChatFormatting formatting) {
        this.color = Optional.ofNullable(formatting.getColor()).orElse(0x000000);
        this.formatting = formatting;
    }

    DyeTeamKey(int color) {
        this.color = color;
        this.formatting = closestFormatting(color);
    }

    private ChatFormatting closestFormatting(int color) {
        double minDist = Double.POSITIVE_INFINITY;
        ChatFormatting closest = null;

        for (ChatFormatting formatting : ChatFormatting.values()) {
            Integer colorValue = formatting.getColor();

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
    public ChatFormatting formatting() {
        return formatting;
    }

    public static @Nullable DyeTeamKey byId(String id) {
        try {
            return DyeTeamKey.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
