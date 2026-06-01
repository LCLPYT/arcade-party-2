package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.ChatFormatting.AQUA;
import static net.minecraft.ChatFormatting.GREEN;

public interface KitHandle {

    Identifier gameId();

    HookRegistrar hooks();

    TaskScheduler scheduler();

    Translations translations();

    RegistryAccess registries();

    KitReadView readView();

    default boolean hasKitEquipped(ServerPlayer player, Kit kit) {
        return readView().hasKitEquipped(player, kit);
    }

    default ItemStack createKitIcon(Kit kit, ServerPlayer player) {
        ItemStack stack = kit.createItemStack(registries());

        decorateItemStack(stack, kit, player, true);

        return stack;
    }

    default ItemStack createItemStack(Kit kit, ServerPlayer player) {
        ItemStack stack = kit.createItemStack(registries());

        decorateItemStack(stack, kit, player, false);

        return stack;
    }

    default void decorateItemStack(ItemStack stack, Kit kit, ServerPlayer player, boolean forIcon) {
        Identifier gameId = gameId();
        Translations translations = translations();

        stack.set(DataComponents.CUSTOM_NAME, kitName(kit).translateFor(player).formatted(AQUA)
                .styled(style -> style.withItalic(false)));

        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
                .withHidden(DataComponents.UNBREAKABLE, true)
                .withHidden(DataComponents.ENCHANTMENTS, true)
                .withHidden(DataComponents.DAMAGE, true));

        String descriptionPath = forIcon ? "description" : "hint";
        String descriptionKey = "game.%s.%s.kit.%s.%s"
                .formatted(gameId.getNamespace(), gameId.getPath(), kit.id(), descriptionPath);

        if (!translations.getTranslator().hasTranslation(translations.getLanguage(player), descriptionKey)) return;

        RootText description = translations.translateText(player, descriptionKey).formatted(GREEN);

        List<Component> currentLore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines();
        List<Component> loreToAdd = IconMaker.wrapText(description, 32);
        List<Component> newLore = new ArrayList<>(currentLore);

        if (!currentLore.isEmpty()) {
            // newline between the lore
            newLore.add(Component.nullToEmpty(""));
        }

        newLore.addAll(loreToAdd);

        ItemStackUtil.setLore(stack, newLore);
    }

    default TranslatedText kitName(Kit kit) {
        Identifier gameId = gameId();

        return translations().translateText("game.%s.%s.kit.%s".formatted(gameId.getNamespace(), gameId.getPath(), kit.id()));
    }
}
