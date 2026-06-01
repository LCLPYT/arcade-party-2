package work.lclpnet.ap2.game.kit

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.TooltipDisplay
import work.lclpnet.ap2.impl.util.IconMaker
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.inv.item.ItemStackUtil
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

interface KitHandle {
    val gameId: Identifier
    val hooks: HookRegistrar
    val scheduler: TaskScheduler
    val translations: Translations
    val registries: RegistryAccess
    val readView: KitReadView

    fun hasKitEquipped(player: ServerPlayer, kit: Kit): Boolean {
        return readView.hasKitEquipped(player, kit)
    }

    fun createKitIcon(kit: Kit, player: ServerPlayer): ItemStack {
        val stack = kit.createItemStack(registries)

        decorateItemStack(stack, kit, player, true)

        return stack
    }

    fun createItemStack(kit: Kit, player: ServerPlayer): ItemStack {
        val stack = kit.createItemStack(registries)

        decorateItemStack(stack, kit, player, false)

        return stack
    }

    fun decorateItemStack(stack: ItemStack, kit: Kit, player: ServerPlayer, forIcon: Boolean) {
        stack.set(
            DataComponents.CUSTOM_NAME, kitName(kit).translateFor(player).formatted(ChatFormatting.AQUA)
                .styled { style -> style.withItalic(false) }
        )

        stack.set(
            DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
                .withHidden(DataComponents.UNBREAKABLE, true)
                .withHidden(DataComponents.ENCHANTMENTS, true)
                .withHidden(DataComponents.DAMAGE, true)
        )

        val descriptionPath = if (forIcon) "description" else "hint"
        val descriptionKey = "game.${gameId.namespace}.${gameId.path}.kit.${kit.id()}.$descriptionPath"

        if (!translations.translator.hasTranslation(translations.getLanguage(player), descriptionKey)) return

        val description = translations.translateText(player, descriptionKey).formatted(ChatFormatting.GREEN)

        val currentLore = stack.getOrDefault(
            DataComponents.LORE,
            ItemLore.EMPTY
        ).styledLines()

        val loreToAdd = IconMaker.wrapText(description, 32)
        val newLore: MutableList<Component> = ArrayList(currentLore)

        if (!currentLore.isEmpty()) {
            // newline between the lore
            newLore.add(Component.nullToEmpty(""))
        }

        newLore.addAll(loreToAdd)

        ItemStackUtil.setLore(stack, newLore)
    }

    fun kitName(kit: Kit): TranslatedText {
        return translations.translateText("game.${gameId.namespace}.${gameId.path}.kit.${kit.id()}")
    }
}
