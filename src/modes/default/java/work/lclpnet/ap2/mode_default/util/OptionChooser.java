package work.lclpnet.ap2.mode_default.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.type.RestrictedInventory;

import java.util.Collection;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.lang.Math.ceil;
import static java.lang.Math.clamp;

public class OptionChooser<T> {

    private final WeakHashMap<Container, ChooserInventory<T>> inventories = new WeakHashMap<>();

    @Nullable
    public T get(Container inv, int slot) {
        ChooserInventory<T> chooser = inventories.get(inv);

        if (chooser == null) return null;

        return chooser.get(slot);
    }

    public void registerInventory(RestrictedInventory inv, Collection<T> items) {
        inventories.put(inv, new ChooserInventory<>(items));
    }

    public RestrictedInventory createInventory(Collection<T> items, Component title, Function<T, ItemStack> iconFactory) {
        int rows = clamp((int) ceil(items.size() / 9d), 1, 6);

        RestrictedInventory inv = new RestrictedInventory(rows, title);

        int capacity = rows * 9;
        int i = 0;

        for (T item : items) {
            if (i >= capacity) break;

            ItemStack icon = iconFactory.apply(item);

            inv.setItem(i++, icon);
        }

        this.registerInventory(inv, items);

        return inv;
    }

    public void listen(HookRegistrar hooks, BiConsumer<T, ServerPlayer> action) {
        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> {
            this.onModifyInventory(event, action);
            return false;
        });
    }

    private void onModifyInventory(PlayerInventoryHooks.ClickEvent event, BiConsumer<T, ServerPlayer> action) {
        if (event.action() != ContainerInput.PICKUP) return;

        ServerPlayer player = event.player();
        MinecraftServer server = player.level().getServer();

        if (!Commands.LEVEL_GAMEMASTERS.check(server.getProfilePermissions(player.nameAndId()))) return;

        Container inventory = event.inventory();

        if (inventory == null) return;

        Slot slot = event.handlerSlot();

        if (slot == null) return;

        int slotIndex = slot.getContainerSlot();

        T item = get(inventory, slotIndex);

        if (item == null) return;

        action.accept(item, player);

        player.closeContainer();
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 2f);
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
