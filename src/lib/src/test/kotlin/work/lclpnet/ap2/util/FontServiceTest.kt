package work.lclpnet.ap2.util

import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import work.lclpnet.kibu.assets.AssetManager
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class FontServiceTest {

    private val logger = LoggerFactory.getLogger(FontServiceTest::class.java)

    /**
     * A synthetic 3x1 glyph font: 'A' has its rightmost pixel at local column 4 (advance 6),
     * 'B' at local column 2 (advance 4), and ' ' at local column 6 (advance 8). The space
     * provider also defines ' ' = 4, which must take precedence over the bitmap glyph.
     */
    private fun fontTexture(): ByteArray {
        val image = BufferedImage(24, 8, BufferedImage.TYPE_INT_ARGB)
        val white = 0xFFFFFFFF.toInt()
        image.setRGB(4, 0, white)   // 'A', cell 0, local column 4
        image.setRGB(10, 0, white)  // 'B', cell 1, local column 2
        image.setRGB(22, 0, white)  // ' ', cell 2, local column 6
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun service(): FontService {
        val zwj = String(Character.toChars(0x200c))  // zero-width non-joiner
        val assets = mapOf(
            "assets/minecraft/font/include/space.json" to
                    """{"providers":[{"type":"space","advances":{" ":4,"$zwj":0}}]}""".toByteArray(),
            "assets/minecraft/font/include/default.json" to
                    """{"providers":[{"type":"bitmap","file":"minecraft:font/test.png","ascent":7,"chars":["AB "]}]}""".toByteArray(),
            "assets/minecraft/textures/font/test.png" to fontTexture()
        )

        val service = FontService(mock(AssetManager::class.java), logger)
        service.computeAdvances { name -> assets[name]?.let { ByteArrayInputStream(it) } }
        return service
    }

    @Test
    fun advance_bitmapGlyphs_matchRightmostColumn() {
        val font = service()
        assertEquals(6f, font.advance('A'.code, false), DELTA)
        assertEquals(4f, font.advance('B'.code, false), DELTA)
    }

    @Test
    fun advance_spaceProviderTakesPrecedence() {
        val font = service()
        assertEquals(4f, font.advance(' '.code, false), DELTA)  // 4 (space.json) wins over 8 (bitmap)
        assertEquals(0f, font.advance(0x200c, false), DELTA)
    }

    @Test
    fun advance_unknownCodepoint_fallsBackToDefault() {
        val font = service()
        assertEquals(FontService.DEFAULT_ADVANCE.toFloat(), font.advance('Z'.code, false), DELTA)
    }

    @Test
    fun advance_bold_addsOnePixel() {
        val font = service()
        assertEquals(7f, font.advance('A'.code, true), DELTA)
    }

    @Test
    fun width_string_sumsAdvances() {
        val font = service()
        assertEquals(10f, font.width("AB"), DELTA)               // 6 + 4
        assertEquals(font.width("AB") + 2f, font.width("AB", true), DELTA)
    }

    @Test
    fun width_component_honorsBold() {
        val font = service()
        assertEquals(10f, font.width(Component.literal("AB")), DELTA)
        assertEquals(12f, font.width(Component.literal("AB").withStyle(ChatFormatting.BOLD)), DELTA)
    }

    companion object {
        private const val DELTA = 1e-4f

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
}
