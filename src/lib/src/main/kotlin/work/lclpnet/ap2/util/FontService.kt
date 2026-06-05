package work.lclpnet.ap2.util

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.jetbrains.annotations.Blocking
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.kibu.assets.AssetManager
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.*
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Estimates the rendered pixel width of strings in Minecraft's default font on the server side.
 *
 * The advance (width including the 1px spacing) of every glyph of the default font is computed
 * from the font bitmap textures, replicating the algorithm the client uses in
 * `net.minecraft.client.gui.font.providers.BitmapProvider`. The required assets are read at
 * runtime from the Minecraft client jar via the kibu [AssetManager].
 */
class FontService(private val assetManager: AssetManager, private val logger: Logger) {

    private val advances = Int2IntOpenHashMap().apply { defaultReturnValue(DEFAULT_ADVANCE) }

    @Volatile
    private var initialized = false

    @Blocking
    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true

        val clientJar = assetManager.getDownload(AssetManager.DOWNLOAD_CLIENT)

        if (clientJar == null) {
            logger.error("Failed to download client jar, font widths will be estimated")
            return
        }

        try {
            ZipFile(clientJar.toFile()).use { zip ->
                computeAdvances { name -> zip.getEntry(name)?.let(zip::getInputStream) }
            }
        } catch (e: Exception) {
            logger.error("Failed to compute font advances, font widths will be estimated", e)
        }
    }

    /**
     * Populates the advance table from the default font assets.
     * The [open] function resolves an asset path (e.g. `assets/minecraft/font/include/default.json`)
     * to an input stream, or null if it does not exist.
     */
    internal fun computeAdvances(open: (String) -> InputStream?) {
        // the space provider takes precedence over the bitmap glyphs of the default font
        readSpaceAdvances(open)
        readBitmapAdvances(open)
    }

    private fun readSpaceAdvances(open: (String) -> InputStream?) {
        val json = readJson(open, "assets/minecraft/font/include/space.json") ?: return
        val providers = json.optJSONArray("providers") ?: return

        for (i in 0 until providers.length()) {
            val provider = providers.optJSONObject(i) ?: continue
            if (provider.optString("type") != "space") continue

            val advances = provider.optJSONObject("advances") ?: continue

            for (key in advances.keySet()) {
                if (key.isEmpty()) continue
                val advance = advances.getDouble(key).roundToInt()
                putIfAbsent(key.codePointAt(0), advance)
            }
        }
    }

    private fun readBitmapAdvances(open: (String) -> InputStream?) {
        val json = readJson(open, "assets/minecraft/font/include/default.json") ?: return
        val providers = json.optJSONArray("providers") ?: return

        for (i in 0 until providers.length()) {
            val provider = providers.optJSONObject(i) ?: continue
            if (provider.optString("type") != "bitmap") continue

            readBitmapProvider(open, provider)
        }
    }

    private fun readBitmapProvider(open: (String) -> InputStream?, provider: JSONObject) {
        val file = provider.optString("file", null) ?: return
        val height = provider.optInt("height", 8)

        val rows = provider.optJSONArray("chars") ?: return
        if (rows.length() == 0) return

        val grid = Array(rows.length()) { rows.getString(it).codePoints().toArray() }
        val cols = grid[0].size
        if (cols == 0) return

        val image = open(texturePath(file))?.use { ImageIO.read(it) }

        if (image == null) {
            logger.warn("Failed to read font texture {}", file)
            return
        }

        val glyphWidth = image.width / cols
        val glyphHeight = image.height / rows.length()
        val pixelScale = height.toFloat() / glyphHeight
        val hasAlpha = image.colorModel.hasAlpha()

        for (slotY in grid.indices) {
            val line = grid[slotY]
            for (slotX in line.indices) {
                val codePoint = line[slotX]
                if (codePoint == 0) continue

                val actualWidth = actualGlyphWidth(image, glyphWidth, glyphHeight, slotX, slotY, hasAlpha)
                val advance = (0.5 + actualWidth * pixelScale).toInt() + 1
                putIfAbsent(codePoint, advance)
            }
        }
    }

    private fun actualGlyphWidth(image: BufferedImage, glyphWidth: Int, glyphHeight: Int,
                                 slotX: Int, slotY: Int, hasAlpha: Boolean): Int {
        for (x in glyphWidth - 1 downTo 0) {
            val xPixel = slotX * glyphWidth + x
            for (y in 0 until glyphHeight) {
                val yPixel = slotY * glyphHeight + y
                if (isOpaque(image.getRGB(xPixel, yPixel), hasAlpha)) {
                    return x + 1
                }
            }
        }
        return 0
    }

    private fun isOpaque(argb: Int, hasAlpha: Boolean): Boolean {
        return if (hasAlpha) (argb ushr 24) != 0 else (argb and 0xffffff) != 0
    }

    private fun putIfAbsent(codePoint: Int, advance: Int) {
        if (!advances.containsKey(codePoint)) {
            advances.put(codePoint, advance)
        }
    }

    private fun readJson(open: (String) -> InputStream?, path: String): JSONObject? {
        val content = open(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

        if (content == null) {
            logger.warn("Font asset {} not found", path)
            return null
        }

        return JSONObject(content)
    }

    private fun texturePath(file: String): String {
        val idx = file.indexOf(':')
        val namespace = if (idx >= 0) file.substring(0, idx) else "minecraft"
        val path = if (idx >= 0) file.substring(idx + 1) else file
        return "assets/$namespace/textures/$path"
    }

    /** The advance (width in pixels) of a single code point, optionally bold. */
    fun advance(codePoint: Int, bold: Boolean): Float {
        return (advances.get(codePoint) + if (bold) 1 else 0).toFloat()
    }

    /** The rendered width of [text] in pixels. */
    @JvmOverloads
    fun width(text: CharSequence, bold: Boolean = false): Float {
        var acc = 0f
        var i = 0
        while (i < text.length) {
            val codePoint = Character.codePointAt(text, i)
            acc += advance(codePoint, bold)
            i += Character.charCount(codePoint)
        }
        return acc
    }

    /** The rendered width of [component] in pixels, honoring bold styling of each part. */
    fun width(component: Component): Float {
        var acc = 0f
        component.visit({ style: Style, text: String ->
            acc += width(text, style.isBold)
            Optional.empty<Any>()
        }, Style.EMPTY)
        return acc
    }

    companion object {
        const val DEFAULT_ADVANCE = 6
    }
}
