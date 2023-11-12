package work.lclpnet.ap2.base.util;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.AQUA;
import static net.minecraft.util.Formatting.GREEN;

public class MapIconMaker {

    public static ItemStack createIcon(MiniGame game, ServerPlayerEntity player, TranslationService translations) {
        ItemStack icon = game.getIcon();

        icon.setCustomName(translations.translateText(player, game.getTitleKey())
                .styled(style -> style.withItalic(false).withFormatting(AQUA)));

        String descriptionKey = game.getDescriptionKey();
        Object[] descArgs = game.getDescriptionArguments();

        ItemStackUtil.setLore(icon, wrapText(translations.translateText(player, descriptionKey, descArgs)
                .formatted(GREEN), 24));

        return icon;
    }

    public static List<Text> wrapText(Text text, int charsPerLine) {
        List<Text> wrapped = new ArrayList<>();
        Style style = text.getStyle();

        String str = text.getString();
        String[] words = str.split("\\s+");

        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            int len = builder.length();
            int wordLen = word.length();

            if (len + wordLen <= charsPerLine) {
                if (builder.isEmpty()) {
                    builder.append(word);
                    continue;
                }

                builder.append(' ').append(word);
                continue;
            }

            if (!builder.isEmpty()) {
                wrapped.add(Text.literal(builder.toString()).setStyle(style));
                builder.setLength(0);
            }

            if (wordLen > charsPerLine) {
                wrapped.add(Text.literal(word).setStyle(style));
            }
        }

        if (!builder.isEmpty()) {
            wrapped.add(Text.literal(builder.toString()).setStyle(style));
        }

        return wrapped;
    }

    private MapIconMaker() {}
}
