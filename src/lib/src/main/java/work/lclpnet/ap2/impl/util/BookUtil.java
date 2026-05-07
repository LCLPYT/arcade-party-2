package work.lclpnet.ap2.impl.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookUtil {

    public static Builder builder(String title, String author) {
        return new Builder(title, author);
    }

    private BookUtil() {}

    public static class Builder {
        private final String title, author;
        private final List<Component> pages = new ArrayList<>();

        private Builder(String title, String author) {
            this.title = title;
            this.author = author;
        }

        public Builder addPage(Component... lines) {
            MutableComponent root = Component.empty();

            for (Component line : lines) {
                root.append(line);
            }

            pages.add(root);

            return this;
        }

        public void applyTo(ItemStack stack) {
            var titlePair = new Filterable<>(title, Optional.empty());

            var pagePairs = pages.stream()
                    .map(page -> new Filterable<>(page, Optional.empty()))
                    .toList();

            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(titlePair, author, 0, pagePairs, true));
        }
    }
}
