package work.lclpnet.ap2.game.mimicry.data;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;
import java.util.function.BiConsumer;

public class MimicryManager {

    private final MiniGameHandle gameHandle;
    private final Map<UUID, MimicryRoom> rooms;
    private final BlockBox buttons;
    private final Random random;
    private final ServerWorld world;
    private final Runnable allCompleteCallback;
    private final IntList sequence = new IntArrayList();
    private final Object2IntMap<UUID> progress = new Object2IntOpenHashMap<>();
    private final float[] buttonPitches;
    private boolean replay = false;
    private final Map<UUID, TaskHandle> deactivation = new HashMap<>();

    public MimicryManager(MiniGameHandle gameHandle, Map<UUID, MimicryRoom> rooms, BlockBox buttons, Random random,
                          ServerWorld world, Runnable allCompleteCallback) {
        this.rooms = rooms;
        this.gameHandle = gameHandle;
        this.buttons = buttons;
        this.random = random;
        this.world = world;
        this.allCompleteCallback = allCompleteCallback;

        int buttonCount = buttonCount();
        this.buttonPitches = new float[buttonCount];

        for (int i = 0; i < buttonCount; i++) {
            buttonPitches[i] = SoundHelper.getPitch(i % 25);
        }
    }

    public void eachParticipant(BiConsumer<ServerPlayerEntity, MimicryRoom> action) {
        Participants participants = gameHandle.getParticipants();
        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();

        rooms.forEach((uuid, room) -> {
            if (!participants.isParticipating(uuid)) return;

            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player == null) return;

            action.accept(player, room);
        });
    }

    public void extendSequence() {
        int buttonCount = buttonCount();

        if (buttonCount <= 0) {
            throw new IllegalStateException("There are no buttons");
        }

        sequence.add(random.nextInt(buttonCount));
    }

    public int sequenceLength() {
        return sequence.size();
    }

    public int sequenceItem(int i) {
        return sequence.getInt(i);
    }

    public int buttonCount() {
        // buttons box should be a plane, so one dimension should be 1. Otherwise, this will not work
        return buttons.volume();
    }

    /**
     * Called when a player clicks a button.
     * @param player The player
     * @param pos The button position.
     * @return True, if the player should be eliminated.
     */
    public boolean onInputButton(ServerPlayerEntity player, BlockPos pos) {
        if (!replay) return false;

        UUID uuid = player.getUuid();
        MimicryRoom room = rooms.get(uuid);

        if (room == null) return false;

        int button = room.buttonIndex(pos);

        if (button == -1) return false;

        int offset = progress.getOrDefault(uuid, 0);

        if (offset >= sequence.size()) return false;

        int expected = sequence.getInt(offset);

        if (expected != button) {
            return true;
        }

        int newOffset = offset + 1;
        progress.put(uuid, newOffset);

        float pitch = getButtonPitch(button);
        SoundHelper.playSound(player, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS,
                pos.getX(), pos.getY(), pos.getZ(), 0.5f, pitch);

        activateButton(room, button, uuid);

        if (newOffset == sequence.size()) {
            onCompleteSequence(player);
        }

        return false;
    }

    private void activateButton(MimicryRoom room, int button, UUID uuid) {
        room.setButtonActive(button, world);

        TaskHandle handle = deactivation.get(uuid);

        if (handle != null) {
            handle.cancel();
        }

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        // defer deactivation 15 ticks
        deactivation.put(uuid, scheduler.timeout(() -> room.resetActiveButton(world), 15));
    }

    private void onCompleteSequence(ServerPlayerEntity player) {
        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.mimicry.correct")
                .formatted(Formatting.GREEN);

        player.sendMessage(msg);
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

        if (getPlayersToEliminate().isEmpty()) {
            allCompleteCallback.run();
        }
    }

    public void setReplay(boolean replay) {
        this.replay = replay;
    }

    public List<ServerPlayerEntity> getPlayersToEliminate() {
        int sequenceLength = sequenceLength();

        return gameHandle.getParticipants().stream()
                .filter(player -> progress.getOrDefault(player.getUuid(), 0) < sequenceLength)
                .toList();
    }

    public void reset() {
        progress.clear();
    }

    public float getButtonPitch(int button) {
        return buttonPitches[button % buttonPitches.length];
    }
}
