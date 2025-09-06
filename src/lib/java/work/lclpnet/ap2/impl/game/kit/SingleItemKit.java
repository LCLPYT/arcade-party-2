package work.lclpnet.ap2.impl.game.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.util.ItemHelper;

import java.util.Optional;

public class SingleItemKit extends BaseKit {

    private static final MapCodec<String> KIT_CODEC = Codec.STRING.fieldOf("ap2:kit");

    @Getter
    private final Item item;
    private final int count;

    protected SingleItemKit(KitHandle handle, String id, Item item, int count) {
        super(handle, id);
        this.item = item;
        this.count = count;
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        ItemStack stack = new ItemStack(item);

        configureItemStack(stack);

        return stack;
    }

    public void configureItemStack(ItemStack stack) {
        ItemHelper.setCustomData(stack, KIT_CODEC, id);
    }

    @Override
    public void equip(ServerPlayerEntity player, KitOptions options) {
        ItemStack stack = handle.createItemStack(this, player);
        stack.setCount(count);

        player.getInventory().setStack(options.mainItemSlot(), stack);
    }

    @Override
    public void unequip(ServerPlayerEntity player, KitOptions options) {
        player.getInventory().removeStack(options.mainItemSlot());
    }

    public static Optional<String> getId(ItemStack stack) {
        return ItemHelper.getCustomData(stack, KIT_CODEC);
    }

    public static Optional<SingleItemKit> get(ItemStack stack, KitManager kitManager) {
        return SingleItemKit.getId(stack)
                .flatMap(kitManager::byId)
                .map(k -> k instanceof SingleItemKit sik ? sik : null);
    }
}
