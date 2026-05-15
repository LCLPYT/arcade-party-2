package work.lclpnet.ap2.impl.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.inv.item.ItemStackUtil.setLore;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class IconMaker {

    public static ItemStack createIcon(GameMap map, ServerPlayer player, Translations translations, DataManager dataManager) {
        ItemStack icon = new ItemStack(map.getIcon());

        String name = map.getName(translations.getLanguage(player));

        icon.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                .withStyle(style -> style.withItalic(false).applyFormat(AQUA)));

        List<String> authors = map.getAuthors().stream().map(dataManager::string).toList();

        if (!authors.isEmpty()) {
            setLore(icon, wrapText(translations.translateText(player, "ap2.built_by",
                            styled(String.join(", ", authors), YELLOW))
                    .formatted(GREEN), 32));
        }

        return icon;
    }

    public static ItemStack createIcon(MiniGame game, ServerPlayer player, Translations translations) {
        RegistryAccess registryManager = player.level().registryAccess();
        ItemStack icon = game.getIcon(registryManager);

        icon.set(DataComponents.CUSTOM_NAME, translations.translateText(player, game.getTitleKey())
                .styled(style -> style.withItalic(false).applyFormat(AQUA)));

        String descriptionKey = game.getDescriptionKey();
        Object[] descArgs = game.getDescriptionArguments();

        setLore(icon, wrapText(translations.translateText(player, descriptionKey, descArgs)
                .formatted(GREEN), 32));

        icon.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
                .withHidden(DataComponents.UNBREAKABLE, true)
                .withHidden(DataComponents.ENCHANTMENTS, true)
                .withHidden(DataComponents.DAMAGE, true));

        return icon;
    }

    public static List<Component> wrapText(Component text, int charsPerLine) {
        Style style = text.getStyle();

        return wrapText(text.getString(), charsPerLine).stream()
                .<Component>map(line -> Component.literal(line).setStyle(style))
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
