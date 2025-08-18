package work.lclpnet.ap2.api.util;

import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.util.function.UnaryOperator;

@SuppressWarnings("unchecked")
public interface StyleTransformer<Self extends StyleTransformer<Self>> {

    Style getStyle();

    void setStyle(Style style);

    /**
     * Updates the style.
     *
     * @see #getStyle()
     * @see #setStyle(Style)
     *
     * @param styleUpdater the style updater
     */
    default Self styled(UnaryOperator<Style> styleUpdater) {
        this.setStyle(styleUpdater.apply(this.getStyle()));
        return (Self) this;
    }

    /**
     * Fills the absent parts of the style with definitions from {@code styleOverride}.
     *
     * @see Style#withParent(Style)
     *
     * @param styleOverride the style that provides definitions for absent definitions in the title text's style
     */
    default Self fillStyle(Style styleOverride) {
        this.setStyle(styleOverride.withParent(this.getStyle()));
        return (Self) this;
    }

    /**
     * Adds some formattings to the style.
     *
     * @param formattings an array of formattings
     */
    default Self formatted(Formatting... formattings) {
        this.setStyle(this.getStyle().withFormatting(formattings));
        return (Self) this;
    }

    /**
     * Add a formatting to the style.
     *
     * @param formatting a formatting
     */
    default Self formatted(Formatting formatting) {
        this.setStyle(this.getStyle().withFormatting(formatting));
        return (Self) this;
    }
}
