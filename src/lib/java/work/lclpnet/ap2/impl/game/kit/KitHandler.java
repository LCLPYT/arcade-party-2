package work.lclpnet.ap2.impl.game.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.GameCommons;
import work.lclpnet.ap2.impl.util.CustomNbt;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.prompt.OptionPrompt;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class KitHandler {

    private static final MapCodec<Boolean> KIT_SELECTOR_CODEC = Codec.BOOL.fieldOf("ap2:kit_selector");
    private static final Item KIT_SELECTOR_ITEM = Items.NETHER_STAR;

    @Getter
    private final KitManager manager;
    private final Participants participants;
    private final KitHandle kitHandle;
    private final Set<UUID> mayChangeKit = new HashSet<>();

    public KitHandler(KitManager manager, Participants participants, KitHandle kitHandle) {
        this.manager = manager;
        this.participants = participants;
        this.kitHandle = kitHandle;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayer player) || !participants.isParticipating(player)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);

            if (isKitSelector(stack) && canChangeKit(player)) {
                openKitSelector(player);
                return InteractionResult.SUCCESS_SERVER;
            }

            return InteractionResult.PASS;
        });
    }

    public synchronized void openKitSelector(ServerPlayer player) {
        RootText title = kitHandle.translations().translateText(player, "ap2.kit_selector");
        List<Kit> kits = manager.getKits();

        OptionPrompt.open(player, title, kits, kit -> kitHandle.createKitIcon(kit, player))
                .thenAccept(optKit -> optKit
                        .ifPresent(kit -> changeKit(player, kit)));
    }

    public synchronized void changeKit(ServerPlayer player, Kit kit) {
        if (!mayChangeKit.contains(player.getUUID())) return;

        manager.changeKit(player, kit);

        kitHandle.translations().translateText("ap2.kit_selector.selected", kitHandle.kitName(kit).formatted(ChatFormatting.AQUA))
                .formatted(ChatFormatting.GREEN)
                .sendTo(player);

        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2f);
    }

    public synchronized boolean canChangeKit(ServerPlayer player) {
        return mayChangeKit.contains(player.getUUID());
    }

    public void setupPlayerKits() {
        manager.setupPlayerKits(participants);
    }

    public void enableKitChanger() {
        participants.forEach(this::enableKitChanger);
    }

    public synchronized void enableKitChanger(ServerPlayer player) {
        mayChangeKit.add(player.getUUID());

        var stack = new ItemStack(KIT_SELECTOR_ITEM);

        stack.set(DataComponents.ITEM_NAME, kitHandle.translations().translateText(player, "ap2.kit_selector")
                .formatted(ChatFormatting.AQUA));

        CustomNbt.set(stack, KIT_SELECTOR_CODEC, true);

        player.getInventory().setItem(manager.getOptions().kitSelectorSlot(), stack);
    }

    public void disableKitChanger() {
        participants.forEach(this::disableKitChanger);
    }

    public synchronized void disableKitChanger(ServerPlayer player) {
        mayChangeKit.remove(player.getUUID());

        closeKitChanger(player);

        player.getInventory().removeItemNoUpdate(manager.getOptions().kitSelectorSlot());
    }

    public void closeKitChanger() {
        participants.forEach(this::closeKitChanger);
    }

    public void closeKitChanger(ServerPlayer player) {
        player.closeContainer();
    }

    public boolean isKitSelector(ItemStack stack) {
        if (!stack.is(KIT_SELECTOR_ITEM)) return false;

        return CustomNbt.get(stack, KIT_SELECTOR_CODEC).orElse(false);
    }

    public void selectKitChanger() {
        for (ServerPlayer player : participants) {
            PlayerInventoryAccess.setSelectedSlot(player, manager.getOptions().kitSelectorSlot());
        }
    }

    public void selectKitItem() {
        for (ServerPlayer player : participants) {
            PlayerInventoryAccess.setSelectedSlot(player, manager.getOptions().mainItemSlot());
        }
    }

    public void startKitSelectionTimer(GameCommons commons, Runnable onComplete) {
        startKitSelectionTimer(commons, Ticks.seconds(10), onComplete);
    }

    public void startKitSelectionTimer(GameCommons commons, int ticks, Runnable onComplete) {
        if (manager.getKits().size() < 2) {
            onComplete.run();
            return;
        }

        commons.announcer().announceSubtitle("ap2.kit_selector.hint");

        selectKitChanger();

        TranslatedText label = kitHandle.translations().translateText("ap2.kit_selection");

        commons.createTimerTicks(label, ticks).whenDone(onComplete);
    }

    /**
     * Performs common kit setup.
     * This includes:
     * - initializing the kit manager
     * - initializing all registered kits
     * - enabling the kit changer item, giving the item to the players
     */
    public void setup() {
        manager.init();

        init(kitHandle.hooks());
        setupPlayerKits();
        enableKitChanger();
        selectKitChanger();
    }

    public static KitHandler create(MiniGameHandle gameHandle, ServerLevel world, Function<KitHandle, List<Kit>> kitsFactory) {
        var readView = new ProxyKitReadView();
        var handle = RecordKitHandle.of(gameHandle, world.registryAccess(), readView);

        var manager = new KitManager(kitsFactory.apply(handle));

        readView.inject(manager);

        return new KitHandler(manager, gameHandle.getParticipants(), handle);
    }
}
