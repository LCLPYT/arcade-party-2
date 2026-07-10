package work.lclpnet.ap2.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

interface StyleTransformer<Self : StyleTransformer<Self>> {

    var style: Style

    /**
     * Updates the style.
     *
     * @see .getStyle
     * @see .setStyle
     * @param styleUpdater the style updater
     */
    fun withStyle(styleUpdater: (Style) -> Style): Self {
        style = styleUpdater(style)
        return this as Self
    }

    /**
     * Fills the absent parts of the style with definitions from `styleOverride`.
     *
     * @see Style.applyTo
     * @param styleOverride the style that provides definitions for absent definitions in the title text's style
     */
    fun withStyle(styleOverride: Style): Self {
        style = styleOverride.applyTo(style)
        return this as Self
    }

    /**
     * Adds some formattings to the style.
     *
     * @param formatting an array of formattings
     */
    fun withStyle(vararg formatting: ChatFormatting): Self {
        style = style.applyFormats(*formatting)
        return this as Self
    }

    /**
     * Add a formatting to the style.
     *
     * @param formatting a formatting
     */
    fun withStyle(formatting: ChatFormatting): Self {
        style = style.applyFormat(formatting)
        return this as Self
    }

    /**
     * Set the color of the style.
     *
     * @param color The packed color int.
     */
    fun withColor(color: Int): Self {
        style = style.withColor(color)
        return this as Self
    }

    /**
     * Set the color of the style.
     *
     * @param color The text color.
     */
    fun withColor(color: TextColor): Self {
        style = style.withColor(color)
        return this as Self
    }
}