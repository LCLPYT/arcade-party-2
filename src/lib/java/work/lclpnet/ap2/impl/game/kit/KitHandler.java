package work.lclpnet.ap2.impl.game.kit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.GameCommons;
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
        RootText title = kitHandle.translations().translateText(player, "ap2.kit_selector");
        List<Kit> kits = manager.getKits();

        OptionPrompt.open(player, title, kits, kit -> kitHandle.createKitIcon(kit, player))
                .thenAccept(optKit -> optKit
                        .ifPresent(kit -> changeKit(player, kit)));
    }

    public synchronized void changeKit(ServerPlayerEntity player, Kit kit) {
        if (!mayChangeKit.contains(player.getUuid())) return;

        manager.changeKit(player, kit);

        kitHandle.translations().translateText("ap2.kit_selector.selected", kitHandle.kitName(kit).formatted(Formatting.AQUA))
                .formatted(Formatting.GREEN)
                .sendTo(player);

        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.NEUTRAL, 0.5f, 2f);
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

        var stack = new ItemStack(KIT_SELECTOR_ITEM);

        stack.set(DataComponentTypes.ITEM_NAME, kitHandle.translations().translateText(player, "ap2.kit_selector")
                .formatted(Formatting.AQUA));

        stack.set(DataComponentTypes.CUSTOM_DATA, stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .with(NbtOps.INSTANCE, KIT_SELECTOR_CODEC, true)
                .getOrThrow());

        player.getInventory().setStack(manager.getOptions().kitSelectorSlot(), stack);
    }

    public void disableKitChanger() {
        participants.forEach(this::disableKitChanger);
    }

    public synchronized void disableKitChanger(ServerPlayerEntity player) {
        mayChangeKit.remove(player.getUuid());

        closeKitChanger(player);

        player.getInventory().removeStack(manager.getOptions().kitSelectorSlot());
    }

    public void closeKitChanger() {
        participants.forEach(this::closeKitChanger);
    }

    public void closeKitChanger(ServerPlayerEntity player) {
        player.closeHandledScreen();
    }

    public boolean isKitSelector(ItemStack stack) {
        if (!stack.isOf(KIT_SELECTOR_ITEM)) return false;

        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(KIT_SELECTOR_CODEC)
                .resultOrPartial()
                .orElse(false);
    }

    public void selectKitChanger() {
        for (ServerPlayerEntity player : participants) {
            PlayerInventoryAccess.setSelectedSlot(player, manager.getOptions().kitSelectorSlot());
        }
    }

    public void selectKitItem() {
        for (ServerPlayerEntity player : participants) {
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

    public static KitHandler create(MiniGameHandle gameHandle, ServerWorld world, Function<KitHandle, List<Kit>> kitsFactory) {
        var readView = new ProxyKitReadView();
        var handle = RecordKitHandle.of(gameHandle, world.getRegistryManager(), readView);

        var manager = new KitManager(kitsFactory.apply(handle));

        readView.inject(manager);

        return new KitHandler(manager, gameHandle.getParticipants(), handle);
    }
}
