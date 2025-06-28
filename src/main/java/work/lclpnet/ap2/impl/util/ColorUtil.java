package work.lclpnet.ap2.impl.util;

import java.util.Random;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class ColorUtil {

    private ColorUtil() {}

    public static int getRandomHsvColor(Random random) {
        // hue between 0 and 360
        final float hue = random.nextFloat() * 360;

        return getRandomHsvColor(random, hue);
    }

    public static int getRandomHsvColor(Random random, float hue) {
        // saturation between 0.6 and 1
        final float saturation = random.nextFloat() * 0.4F + 0.6F;
        // value between 0.9 and 1
        final float value = random.nextFloat() * 0.1F + 0.9F;

        return hsvToRgb(hue, saturation, value);
    }

    /**
     * Convert an HSV color to a packed ARGB int.
     * Source: <a href="https://en.wikipedia.org/w/index.php?title=HSL_and_HSV&oldid=1147621409#HSV_to_RGB_alternative">Wikipedia</a>
     * @param hue Hue [0, 360]
     * @param saturation Saturation [0, 1]
     * @param value Value [0, 1]
     * @return A packed argb integer with 8 bits each (alpha, red, green, blue).
     */
    public static int hsvToRgb(final float hue, final float saturation, final float value) {
        final float hueDiv = hue / 60;
        float k;

        k = (5 + hueDiv) % 6;
        final float r = value - value * saturation * max(0, min(min(k, 4 - k), 1));

        k = (3 + hueDiv) % 6;
        final float g = value - value * saturation * max(0, min(min(k, 4 - k), 1));

        k = (1 + hueDiv) % 6;
        final float b = value - value * saturation * max(0, min(min(k, 4 - k), 1));

        return getRgbPacked(
                max(0, min(255, Math.round(255 * r))),
                max(0, min(255, Math.round(255 * g))),
                max(0, min(255, Math.round(255 * b)))
        );
    }

    public static int getRgbPacked(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }

    public static int setArgbPackedAlpha(int color, int alpha) {
        return color | alpha << 24;
    }
}
