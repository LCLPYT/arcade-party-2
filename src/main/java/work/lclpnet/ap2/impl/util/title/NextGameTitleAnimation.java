package work.lclpnet.ap2.impl.util.title;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;

public class NextGameTitleAnimation implements TitleAnimation {

    private static final int DURATION_TICKS = Ticks.seconds(4), FADE_OUT_TICKS = Ticks.seconds(1),
            DURATION_NO_FADE_TICKS = DURATION_TICKS - FADE_OUT_TICKS;
    private final Title handler;
    private final String text, subText;
    private final Style primaryStyle;
    private final Style secondaryStyle;
    private final Style subStyle;
    private int pos = 0;
    private int subTime = 0;

    public NextGameTitleAnimation(ServerPlayerEntity player, Text text, Text subText) {
        this(player, text.getString(), text.getStyle(), text.getStyle().withColor(Formatting.WHITE), subText.getString(), subText.getStyle());
    }

    public NextGameTitleAnimation(ServerPlayerEntity player, String text, Style primaryStyle, Style secondaryStyle,
                                  String subText, Style subStyle) {
        this.handler = Title.get(player);
        this.text = text;
        this.primaryStyle = primaryStyle;
        this.secondaryStyle = secondaryStyle;
        this.subText = subText;
        this.subStyle = subStyle;
    }

    @Override
    public boolean tick() {
        if (pos >= text.length()) {
            // the title is complete, now show the subtitle
            if (subTime++ == 0) {
                handler.times(0, DURATION_NO_FADE_TICKS, FADE_OUT_TICKS);
                handler.title(Text.literal(text).setStyle(primaryStyle), Text.literal(subText).setStyle(subStyle));
            }

            return subTime > DURATION_TICKS;
        }

        var primary = Text.literal(text.substring(0, pos)).setStyle(primaryStyle);
        var secondary = Text.literal(String.valueOf(text.charAt(pos))).setStyle(secondaryStyle);

        handler.title(primary.append(secondary));
        pos++;

        return false;
    }

    @Override
    public void begin() {
        handler.times(0, DURATION_NO_FADE_TICKS, 0);
    }

    @Override
    public void destroy() {
        handler.clear(true);
    }
}
