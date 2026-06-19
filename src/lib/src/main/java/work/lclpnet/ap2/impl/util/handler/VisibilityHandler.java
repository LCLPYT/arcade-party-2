package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.kibu.access.entity.EntityAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityTrackingHooks;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.network.ServerSendPacketCallback;
import work.lclpnet.kibu.hook.util.PendingResult;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.ChatFormatting.*;

public class VisibilityHandler {

    private static final int ITEM_COOLDOWN_TICKS = 20;
    private static final Item ITEM = Items.ENDER_EYE;
    private static final int FLAGS_TRACKED_DATA_ID = EntityAccess.FLAGS.id();
    private final VisibilityManager manager;
    private final Translations translations;
    private final Participants participants;

    public VisibilityHandler(VisibilityManager manager, Translations translations, Participants participants) {
        this.manager = manager;
        this.translations = translations;
        this.participants = participants;
    }

    public void init(HookRegistrar hooks) {
        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, (player, _, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);

            if (!stack.is(ITEM)) {
                return InteractionResult.PASS;
            }

            ItemCooldowns cooldownManager = serverPlayer.getCooldowns();

            if (cooldownManager.isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }

            cooldownManager.addCooldown(stack, ITEM_COOLDOWN_TICKS);

            manager.toggleVisibilityFor(serverPlayer);

            updateItemName(serverPlayer, stack);

            return InteractionResult.FAIL;
        });

        ServerSendPacketCallback.HOOK.registerWith(hooks, this::ensureRelativePlayerVisibility);

        EntityTrackingHooks.START_TRACKING.registerWith(hooks, manager::onStartTracking);

        participants.forEach(manager::updateVisibility);
    }

    public void giveItems() {
        giveItems(8);
    }

    public void giveItems(int slot) {
        for (ServerPlayer player : participants) {
            giveItem(player, slot);
        }
    }

    public void giveItem(ServerPlayer player) {
        giveItem(player, 8);
    }

    public void giveItem(ServerPlayer player, int slot) {
        ItemStack stack = new ItemStack(ITEM);
        updateItemName(player, stack);

        player.getInventory().setItem(slot, stack);
    }

    public Component getItemNameFor(ServerPlayer player) {
        Visibility visibility = manager.getVisibilityFor(player);

        String name = switch (visibility) {
            case VISIBLE -> "visible";
            case PARTIALLY_VISIBLE -> "partially_visible";
            case INVISIBLE -> "invisible";
        };

        ChatFormatting formatting = switch (visibility) {
            case VISIBLE -> GREEN;
            case PARTIALLY_VISIBLE -> YELLOW;
            case INVISIBLE -> RED;
        };

        var status = translations.translateText(player, "ap2.game.visibility.%s".formatted(name)).withStyle(formatting);

        return translations.translateText(player, "ap2.game.visibility", status)
                .setStyle(Style.EMPTY.withItalic(false).withColor(DARK_GREEN));
    }

    public void updateItemName(ServerPlayer player, ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, getItemNameFor(player));
    }

    private PendingResult<Packet<?>> ensureRelativePlayerVisibility(Packet<?> packet, ServerCommonPacketListenerImpl handler) {
        // When a player should not see other participants, or only partially, they have to be invisible for the player.
        // This hook intercepts EntityTrackerUpdateS2CPacket targeting other players and ensures they are invisible.
        // Needs to be done this obscurely, because this needs manipulation of entity data for each player individually.

        // filter packet and visibility, this hook only needs to modify packets when other players should be invisible
        if (!(packet instanceof ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> trackedValues))
            || !(handler instanceof ServerGamePacketListenerImpl networkHandler)
            || manager.getVisibilityFor(networkHandler.player) == Visibility.VISIBLE) {
            return PendingResult.pass();
        }

        // check if the packet target entity is another player
        Entity entity = networkHandler.player.level().getEntity(id);

        if (!(entity instanceof ServerPlayer) || entity == networkHandler.player) {
            return PendingResult.pass();
        }

        // check if the invisibility flag is already set. If not, send a modified packet
        for (int i = 0, size = trackedValues.size(); i < size; i++) {
            var entry = trackedValues.get(i);

            // filter for the flags tracked data entry
            if (entry.id() != FLAGS_TRACKED_DATA_ID) continue;

            byte flags = (byte) entry.value();
            boolean alreadyInvisible = (flags & 1 << EntityAccess.INVISIBLE_FLAG_INDEX) != 0;

            if (alreadyInvisible) {
                // already invisible, send the packet. This also prevents looping in case the packet is re-sent
                return PendingResult.pass();
            }

            var newEntries = modifyEntries(size, i, trackedValues, flags);
            var modifiedPacket = new ClientboundSetEntityDataPacket(id, newEntries);

            return PendingResult.of(modifiedPacket);  // retain the old packet
        }

        // flags entry not present in the update packet
        return PendingResult.pass();
    }

    public boolean isVisibilityChanger(ItemStack stack) {
        return stack.is(ITEM);
    }

    @NotNull
    private static List<SynchedEntityData.DataValue<?>> modifyEntries(int size, int i, List<SynchedEntityData.DataValue<?>> entries, byte flags) {
        var newEntries = new ArrayList<SynchedEntityData.DataValue<?>>(size);

        // copy entries before the flags entry
        for (int j = 0; j < i; j++) {
            newEntries.add(entries.get(j));
        }

        // add a modified flags entry with the invisibility flag set
        flags = EntityAccess.setFlag(flags, EntityAccess.INVISIBLE_FLAG_INDEX, true);
        newEntries.add(SynchedEntityData.DataValue.create(EntityAccess.FLAGS, flags));

        // copy entries after the flags entry
        for (int j = i + 1; j < size; j++) {
            newEntries.add(entries.get(j));
        }

        return newEntries;
    }
}
