package work.lclpnet.ap2.game.book_collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;

public class BookOptions {

    public ArrayList<ItemStack> getBookOptions() {
        ArrayList<ItemStack> bookOptions = new ArrayList<>();
        setBookOptions(bookOptions);
        return bookOptions;
    }

    private void setBookOptions(ArrayList<ItemStack> bookOptions) {
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
        bookOptions.add(new ItemStack(Items.ENCHANTED_BOOK));
    }
}
