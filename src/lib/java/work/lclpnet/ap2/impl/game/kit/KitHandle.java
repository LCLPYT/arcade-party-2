package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.AQUA;
import static net.minecraft.util.Formatting.GREEN;

public interface KitHandle {

    Identifier gameId();

    HookRegistrar hooks();

    TaskScheduler scheduler();

    Translations translations();

    DynamicRegistryManager registries();

    KitReadView readView();

    default boolean hasKitEquipped(ServerPlayerEntity player, Kit kit) {
        return readView().hasKitEquipped(player, kit);
    }

    default ItemStack createKitIcon(Kit kit, ServerPlayerEntity player) {
        ItemStack stack = kit.createItemStack(registries());

        decorateItemStack(stack, kit, player, true);

        return stack;
    }

    default ItemStack createItemStack(Kit kit, ServerPlayerEntity player) {
        ItemStack stack = kit.createItemStack(registries());

        decorateItemStack(stack, kit, player, false);

        return stack;
    }

    default void decorateItemStack(ItemStack stack, Kit kit, ServerPlayerEntity player, boolean forIcon) {
        Identifier gameId = gameId();
        Translations translations = translations();

        stack.set(DataComponentTypes.ITEM_NAME, kitName(kit).translateFor(player).formatted(AQUA));

        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT
                .with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true)
                .with(DataComponentTypes.UNBREAKABLE, true)
                .with(DataComponentTypes.ENCHANTMENTS, true)
                .with(DataComponentTypes.DAMAGE, true));

        String descriptionPath = forIcon ? "description" : "hint";
        String descriptionKey = "game.%s.%s.kit.%s.%s"
                .formatted(gameId.getNamespace(), gameId.getPath(), kit.id(), descriptionPath);

        if (!translations.getTranslator().hasTranslation(translations.getLanguage(player), descriptionKey)) return;

        RootText description = translations.translateText(player, descriptionKey).formatted(GREEN);

        List<Text> currentLore = stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).styledLines();
        List<Text> loreToAdd = IconMaker.wrapText(description, 32);
        List<Text> newLore = new ArrayList<>(currentLore);

        if (!currentLore.isEmpty()) {
            // newline between the lore
            newLore.add(Text.of(""));
        }

        newLore.addAll(loreToAdd);

        ItemStackUtil.setLore(stack, newLore);
    }

    default TranslatedText kitName(Kit kit) {
        Identifier gameId = gameId();

        return translations().translateText("game.%s.%s.kit.%s".formatted(gameId.getNamespace(), gameId.getPath(), kit.id()));
    }
}
