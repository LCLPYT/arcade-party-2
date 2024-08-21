package work.lclpnet.ap2.base.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class IconMaker {

    public static ItemStack createIcon(GameMap map, ServerPlayerEntity player, Translations translations, DataManager dataManager) {
        ItemStack icon = new ItemStack(map.getIcon());

        String name = map.getName(translations.getLanguage(player));

        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name)
                .styled(style -> style.withItalic(false).withFormatting(AQUA)));

        List<String> authors = map.getAuthors().stream().map(dataManager::string).toList();

        if (!authors.isEmpty()) {
            ItemStackUtil.setLore(icon, wrapText(translations.translateText(player, "ap2.built_by",
                            styled(String.join(", ", authors), YELLOW))
                    .formatted(GREEN), 32));
        }

        return icon;
    }

    public static ItemStack createIcon(MiniGame game, ServerPlayerEntity player, Translations translations) {
        ItemStack icon = game.getIcon();

        icon.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, game.getTitleKey())
                .styled(style -> style.withItalic(false).withFormatting(AQUA)));

        String descriptionKey = game.getDescriptionKey();
        Object[] descArgs = game.getDescriptionArguments();

        ItemStackUtil.setLore(icon, wrapText(translations.translateText(player, descriptionKey, descArgs)
                .formatted(GREEN), 32));

        return icon;
    }

    public static List<Text> wrapText(Text text, int charsPerLine) {
        Style style = text.getStyle();

        return wrapText(text.getString(), charsPerLine).stream()
                .<Text>map(line -> Text.literal(line).setStyle(style))
                .toList();
    }

    public static List<String> wrapText(String str, int charsPerLine) {
        List<String> wrapped = new ArrayList<>();

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
                wrapped.add(builder.toString());
                builder.setLength(0);
            }

            if (wordLen <= charsPerLine) {
                builder.append(word);
            } else {
                wrapped.add(word);
            }
        }

        if (!builder.isEmpty()) {
            wrapped.add(builder.toString());
        }

        return wrapped;
    }

    private IconMaker() {}
}
