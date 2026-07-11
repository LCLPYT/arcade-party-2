package work.lclpnet.ap2.impl.util

import net.minecraft.world.item.DyeColor
import java.util.*
import java.util.function.ToDoubleFunction
import kotlin.math.min
import kotlin.math.roundToLong

object ColorUtil {
    fun vividDyeColors(): List<DyeColor> = listOf(
        DyeColor.BROWN,
        DyeColor.RED,
        DyeColor.ORANGE,
        DyeColor.YELLOW,
        DyeColor.LIME,
        DyeColor.GREEN,
        DyeColor.CYAN,
        DyeColor.LIGHT_BLUE,
        DyeColor.BLUE,
        DyeColor.PURPLE,
        DyeColor.MAGENTA,
        DyeColor.PINK,
    )

    @JvmStatic
    fun getRandomHsvColor(random: Random): Int {
        // hue between 0 and 360
        val hue = random.nextFloat() * 360

        return getRandomHsvColor(random, hue)
    }

    @JvmStatic
    fun getRandomHsvColor(random: Random, hue: Float): Int {
        // saturation between 0.6 and 1
        val saturation = random.nextFloat() * 0.4f + 0.6f
        // value between 0.9 and 1
        val value = random.nextFloat() * 0.1f + 0.9f

        return hsvToRgb(hue, saturation, value)
    }

    /**
     * Convert an HSV color to a packed ARGB int.
     * Source: [Wikipedia](https://en.wikipedia.org/w/index.php?title=HSL_and_HSV&oldid=1147621409#HSV_to_RGB_alternative)
     * @param hue Hue [0, 360]
     * @param saturation Saturation [0, 1]
     * @param value Value [0, 1]
     * @return A packed argb integer with 8 bits each (alpha, red, green, blue).
     */
    fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val hueDiv = hue / 60

        var k: Float = (5 + hueDiv) % 6
        val r = value - value * saturation * Math.clamp(min(k, 4 - k), 0f, 1f)

        k = (3 + hueDiv) % 6
        val g = value - value * saturation * Math.clamp(min(k, 4 - k), 0f, 1f)

        k = (1 + hueDiv) % 6
        val b = value - value * saturation * Math.clamp(min(k, 4 - k), 0f, 1f)

        return getRgbPacked(
            Math.clamp((255 * r).roundToLong(), 0, 255),
            Math.clamp((255 * g).roundToLong(), 0, 255),
            Math.clamp((255 * b).roundToLong(), 0, 255)
        )
    }

    fun getRgbPacked(red: Int, green: Int, blue: Int): Int {
        return red shl 16 or (green shl 8) or blue
    }

    fun setArgbPackedAlpha(color: Int, alpha: Int): Int {
        return color or (alpha shl 24)
    }

    @JvmStatic
    fun squaredDistance(color1: Int, color2: Int): Double {
        val r1 = red(color1)
        val g1 = green(color1)
        val b1 = blue(color1)

        val r2 = red(color2)
        val g2 = green(color2)
        val b2 = blue(color2)

        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2

        return (dr * dr + dg * dg + db * db).toDouble()
    }

    fun lerpRgb(start: Int, end: Int, t: Float): Int {
        // Clamp t between 0 and 1
        var t = t
        t = Math.clamp(t, 0f, 1f)

        val r1 = red(start)
        val g1 = green(start)
        val b1 = blue(start)

        val r2 = red(end)
        val g2 = green(end)
        val b2 = blue(end)

        val r = (r1 + (r2 - r1) * t).toInt()
        val g = (g1 + (g2 - g1) * t).toInt()
        val b = (b1 + (b2 - b1) * t).toInt()

        return (r shl 16) or (g shl 8) or b
    }

    fun red(packed: Int): Int {
        return (packed shr 16) and 0xFF
    }

    fun green(packed: Int): Int {
        return (packed shr 8) and 0xFF
    }

    fun blue(packed: Int): Int {
        return packed and 0xFF
    }

    fun closestEntityDyeColor(rgb: Int): DyeColor {
        return Arrays.stream(DyeColor.entries.toTypedArray())
            .min(Comparator.comparingDouble(ToDoubleFunction { c ->
                squaredDistance(
                    c.textureDiffuseColor,
                    rgb
                )
            }))
            .orElseThrow()
    }
}
