package work.lclpnet.ap2.impl.game.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.ap2.impl.util.CustomNbt;

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
    public ItemStack createItemStack(RegistryAccess manager) {
        ItemStack stack = new ItemStack(item);

        configureItemStack(stack);

        return stack;
    }

    public void configureItemStack(ItemStack stack) {
        CustomNbt.set(stack, KIT_CODEC, id);
    }

    @Override
    public void equip(ServerPlayer player, KitOptions options) {
        ItemStack stack = handle.createItemStack(this, player);
        stack.setCount(count);

        player.getInventory().setItem(options.mainItemSlot(), stack);
    }

    @Override
    public void unequip(ServerPlayer player, KitOptions options) {
        player.getInventory().removeItemNoUpdate(options.mainItemSlot());
    }

    public static Optional<String> getId(ItemStack stack) {
        return CustomNbt.get(stack, KIT_CODEC);
    }

    public static Optional<SingleItemKit> get(ItemStack stack, KitManager kitManager) {
        return SingleItemKit.getId(stack)
                .flatMap(kitManager::byId)
                .map(k -> k instanceof SingleItemKit sik ? sik : null);
    }
}
