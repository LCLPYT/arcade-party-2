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

    public static double squaredDistance(int color1, int color2) {
        int r1 = red(color1);
        int g1 = green(color1);
        int b1 = blue(color1);

        int r2 = red(color2);
        int g2 = green(color2);
        int b2 = blue(color2);

        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;

        return dr * dr + dg * dg + db * db;
    }

    public static int lerpRgb(int start, int end, float t) {
        // Clamp t between 0 and 1
        t = Math.max(0, Math.min(1, t));

        int r1 = red(start);
        int g1 = green(start);
        int b1 = blue(start);

        int r2 = red(end);
        int g2 = green(end);
        int b2 = blue(end);

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (r << 16) | (g << 8) | b;
    }

    public static int red(int packed) {
        return (packed >> 16) & 0xFF;
    }

    public static int green(int packed) {
        return (packed >> 8) & 0xFF;
    }

    public static int blue(int packed) {
        return packed & 0xFF;
    }
}
