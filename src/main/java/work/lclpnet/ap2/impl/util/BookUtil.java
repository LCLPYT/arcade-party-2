package work.lclpnet.ap2.impl.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

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

        public void toNbt(NbtCompound nbt) {
            nbt.putString("title", title);
            nbt.putString("author", author);

            NbtList pagesNbt = new NbtList();

            for (Text page : pages) {
                String json = Text.Serialization.toJsonString(page);
                pagesNbt.add(NbtString.of(json));
            }

            nbt.put("pages", pagesNbt);
        }
    }
}
