package work.lclpnet.ap2.base.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.type.RestrictedInventory;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

import java.util.Collection;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DevChooser<T> {

    private final WeakHashMap<Inventory, ChooserInventory<T>> inventories = new WeakHashMap<>();

    @Nullable
    public T get(Inventory inv, int slot) {
        ChooserInventory<T> chooser = inventories.get(inv);

        if (chooser == null) return null;

        return chooser.get(slot);
    }

    public void registerInventory(RestrictedInventory inv, Collection<T> items) {
        inventories.put(inv, new ChooserInventory<>(items));
    }

    public RestrictedInventory createInventory(Collection<T> items, Text title, Function<T, ItemStack> iconFactory) {
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(items.size() / 9d)));

        RestrictedInventory inv = new RestrictedInventory(rows, title);

        int capacity = rows * 9;
        int i = 0;

        for (T item : items) {
            if (i >= capacity) break;

            ItemStack icon = iconFactory.apply(item);

            inv.setStack(i++, icon);
        }

        this.registerInventory(inv, items);

        return inv;
    }

    public void listen(HookRegistrar hooks, BiConsumer<T, ServerPlayerEntity> action) {
        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> {
            this.onModifyInventory(event, action);
            return false;
        });
    }

    private void onModifyInventory(PlayerInventoryHooks.ClickEvent event, BiConsumer<T, ServerPlayerEntity> action) {
        if (event.action() != SlotActionType.PICKUP) return;

        ServerPlayerEntity player = event.player();
        MinecraftServer server = player.getServer();

        if (server == null || server.getPermissionLevel(player.getGameProfile()) < 2) return;

        Inventory inventory = event.inventory();

        if (inventory == null) return;

        Slot slot = event.handlerSlot();

        if (slot == null) return;

        int slotIndex = slot.getIndex();

        T item = get(inventory, slotIndex);

        if (item == null) return;

        action.accept(item, player);

        player.closeHandledScreen();
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.5f, 2f);
    }

    public static class ChooserInventory<T> {
        private final Int2ObjectMap<T> items;

        private ChooserInventory(Collection<T> items) {
            int size = items.size();
            this.items = new Int2ObjectArrayMap<>(size);

            int i = 0;

            for (T item : items) {
                this.items.put(i++, item);
            }
        }

        @Nullable
        public T get(int i) {
            return items.get(i);
        }
    }
}
