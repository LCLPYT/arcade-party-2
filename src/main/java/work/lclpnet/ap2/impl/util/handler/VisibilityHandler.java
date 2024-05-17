package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.kibu.access.entity.EntityAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.network.ServerSendPacketCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.*;

public class VisibilityHandler {

    private static final int ITEM_COOLDOWN_TICKS = 20;
    private static final Item ITEM = Items.ENDER_EYE;
    private static final int FLAGS_TRACKED_DATA_ID = EntityAccess.FLAGS.id();
    private final VisibilityManager manager;
    private final TranslationService translations;
    private final Participants participants;

    public VisibilityHandler(VisibilityManager manager, TranslationService translations, Participants participants) {
        this.manager = manager;
        this.translations = translations;
        this.participants = participants;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(ITEM)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            ItemCooldownManager cooldownManager = serverPlayer.getItemCooldownManager();

            if (cooldownManager.isCoolingDown(ITEM)) {
                return TypedActionResult.fail(ItemStack.EMPTY);
            }

            cooldownManager.set(ITEM, ITEM_COOLDOWN_TICKS);

            manager.toggleVisibilityFor(serverPlayer);

            updateItemName(serverPlayer, stack);

            return TypedActionResult.fail(ItemStack.EMPTY);
        });

        hooks.registerHook(ServerSendPacketCallback.HOOK, this::ensureRelativePlayerVisibility);

        participants.forEach(manager::updateVisibility);
    }

    public void giveItems() {
        for (ServerPlayerEntity player : participants) {
            ItemStack stack = new ItemStack(ITEM);
            updateItemName(player, stack);

            player.getInventory().setStack(8, stack);
        }
    }

    public Text getItemNameFor(ServerPlayerEntity player) {
        Visibility visibility = manager.getVisibilityFor(player);

        String name = switch (visibility) {
            case VISIBLE -> "visible";
            case PARTIALLY_VISIBLE -> "partially_visible";
            case INVISIBLE -> "invisible";
        };

        Formatting formatting = switch (visibility) {
            case VISIBLE -> GREEN;
            case PARTIALLY_VISIBLE -> YELLOW;
            case INVISIBLE -> RED;
        };

        var status = translations.translateText(player, "ap2.game.visibility.%s".formatted(name)).formatted(formatting);

        return translations.translateText(player, "ap2.game.visibility", status)
                .setStyle(Style.EMPTY.withItalic(false).withColor(DARK_GREEN));
    }

    public void updateItemName(ServerPlayerEntity player, ItemStack stack) {
        stack.set(DataComponentTypes.CUSTOM_NAME, getItemNameFor(player));
    }

    private boolean ensureRelativePlayerVisibility(Packet<?> packet, ServerCommonNetworkHandler handler) {
        // When a player should not see other participants, or only partially, they have to be invisible for the player.
        // This hook intercepts EntityTrackerUpdateS2CPacket targeting other players and ensures they are invisible.
        // Needs to be done this obscurely, because this needs manipulation of entity data for each player individually.

        // filter packet and visibility, this hook only needs to modify packets when other players should be invisible
        if (!(packet instanceof EntityTrackerUpdateS2CPacket updatePacket)
            || !(handler instanceof ServerPlayNetworkHandler networkHandler)
            || manager.getVisibilityFor(networkHandler.player) == Visibility.VISIBLE) {
            return false;
        }

        // check if the packet target entity is another player
        Entity entity = networkHandler.player.getServerWorld().getEntityById(updatePacket.id());

        if (!(entity instanceof ServerPlayerEntity) || entity == networkHandler.player) {
            return false;
        }

        var entries = updatePacket.trackedValues();

        // check if the invisibility flag is already set. If not, send a modified packet
        for (int i = 0, size = entries.size(); i < size; i++) {
            var entry = entries.get(i);

            // filter for the flags tracked data entry
            if (entry.id() != FLAGS_TRACKED_DATA_ID) continue;

            byte flags = (byte) entry.value();
            boolean alreadyInvisible = (flags & 1 << EntityAccess.INVISIBLE_FLAG_INDEX) != 0;

            if (alreadyInvisible) {
                // already invisible, send the packet. This also prevents looping in case the packet is re-sent
                return false;
            }

            var newEntries = modifyEntries(size, i, entries, flags);
            var modifiedPacket = new EntityTrackerUpdateS2CPacket(updatePacket.id(), newEntries);
            handler.sendPacket(modifiedPacket);  // this will cause another invocation of this hook, but won't loop

            return true;  // retain the old packet
        }

        // flags entry not present in the update packet
        return false;
    }

    @NotNull
    private static List<DataTracker.SerializedEntry<?>> modifyEntries(int size, int i, List<DataTracker.SerializedEntry<?>> entries, byte flags) {
        var newEntries = new ArrayList<DataTracker.SerializedEntry<?>>(size);

        // copy entries before the flags entry
        for (int j = 0; j < i; j++) {
            newEntries.add(entries.get(i));
        }

        // add a modified flags entry with the invisibility flag set
        flags = EntityAccess.setFlag(flags, EntityAccess.INVISIBLE_FLAG_INDEX, true);
        newEntries.add(DataTracker.SerializedEntry.of(EntityAccess.FLAGS, flags));

        // copy entries after the flags entry
        for (int j = i + 1; j < size; j++) {
            newEntries.add(entries.get(i));
        }

        return newEntries;
    }
}
