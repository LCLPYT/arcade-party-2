package work.lclpnet.ap2.game.dragon_escape.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.prompt.OptionPrompt;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class KitHandler {

    private static final MapCodec<Boolean> KIT_SELECTOR_CODEC = Codec.BOOL.fieldOf("ap2:kit_selector");
    private static final Item KIT_SELECTOR_ITEM = Items.COMPASS;

    private final KitManager manager;
    private final Participants participants;
    private final Translations translations;
    private final KitHandle kitHandle;
    private final Set<UUID> mayChangeKit = new HashSet<>();

    public KitHandler(KitManager manager, Participants participants, Translations translations, KitHandle kitHandle) {
        this.manager = manager;
        this.participants = participants;
        this.translations = translations;
        this.kitHandle = kitHandle;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayerEntity player) || !participants.isParticipating(player)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (isKitSelector(stack) && canChangeKit(player)) {
                openKitSelector(player);
                return ActionResult.SUCCESS_SERVER;
            }

            return ActionResult.PASS;
        });
    }

    public synchronized void openKitSelector(ServerPlayerEntity player) {
        RootText title = translations.translateText(player, "ap2.kit_selector");
        List<Kit> kits = manager.getKits();

        OptionPrompt.open(player, title, kits, kit -> kitHandle.createItemStack(kit, player))
                .thenAccept(optKit -> optKit
                        .ifPresent(kit -> changeKit(player, kit)));
    }

    public synchronized void changeKit(ServerPlayerEntity player, Kit kit) {
        if (!mayChangeKit.contains(player.getUuid())) return;

        manager.changeKit(player, kit);
    }

    public synchronized boolean canChangeKit(ServerPlayerEntity player) {
        return mayChangeKit.contains(player.getUuid());
    }

    public void setupPlayerKits() {
        manager.setupPlayerKits(participants);
    }

    public void enableKitChanger() {
        participants.forEach(this::enableKitChanger);
    }

    public synchronized void enableKitChanger(ServerPlayerEntity player) {
        mayChangeKit.add(player.getUuid());

        var stack = new ItemStack(Items.COMPASS);

        stack.set(DataComponentTypes.ITEM_NAME, translations.translateText(player, "ap2.kit_selector")
                .formatted(Formatting.AQUA));

        stack.set(DataComponentTypes.CUSTOM_DATA, stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .with(NbtOps.INSTANCE, KIT_SELECTOR_CODEC, true)
                .getOrThrow());

        player.getInventory().setStack(8, stack);
    }

    public void disableKitChanger() {
        participants.forEach(this::disableKitChanger);
    }

    public synchronized void disableKitChanger(ServerPlayerEntity player) {
        mayChangeKit.remove(player.getUuid());

        player.closeHandledScreen();

        player.getInventory().removeStack(8);
    }

    public boolean isKitSelector(ItemStack stack) {
        if (!stack.isOf(KIT_SELECTOR_ITEM)) return false;

        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(KIT_SELECTOR_CODEC)
                .resultOrPartial()
                .orElse(false);
    }
}
