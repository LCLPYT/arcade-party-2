package work.lclpnet.ap2.impl.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

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
        private final List<Text> pages = new ArrayList<>();

        private Builder(String title, String author) {
            this.title = title;
            this.author = author;
        }

        public Builder addPage(Text... lines) {
            MutableText root = Text.empty();

            for (Text line : lines) {
                root.append(line);
            }

            pages.add(root);

            return this;
        }

        public void applyTo(ItemStack stack) {
            var titlePair = new RawFilteredPair<>(title, Optional.empty());

            var pagePairs = pages.stream()
                    .map(page -> new RawFilteredPair<>(page, Optional.empty()))
                    .toList();

            stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(titlePair, author, 0, pagePairs, true));
        }
    }
}
