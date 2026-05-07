package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;

import java.util.Random;

import static net.minecraft.ChatFormatting.BOLD;
import static net.minecraft.ChatFormatting.DARK_GREEN;

public class SoundChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(11);
    private static final int SOUND_DELAY_TICKS = 30;
    private static final int REPEAT_DELAY_TICKS = Ticks.seconds(4);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final SoundSubtitles soundSubtitles;
    private SoundEvent correct = null;
    private float pitch = 1f;
    private int correctOption = -1;

    public SoundChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, SoundSubtitles soundSubtitles) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.soundSubtitles = soundSubtitles;
    }

    @Override
    public String id() {
        return "sound";
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_LISTEN;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS + SOUND_DELAY_TICKS + REPEAT_DELAY_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.sound.guess"));

        var soundEvents = soundSubtitles.getSoundEvents();
        var soundOptions = OptionMaker.createOptions(soundEvents, 4, random);

        correctOption = random.nextInt(soundOptions.size());
        correct = soundOptions.get(correctOption);
        randomizePitch();

        var options = soundOptions.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Component[]::new);

        input.expectSelection(options);

        playFirst();
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        stopSound();

        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    private void playFirst() {
        playSound();

        gameHandle.getScheduler().timeout(this::prepareSecond, REPEAT_DELAY_TICKS);
    }

    private void prepareSecond() {
        stopSound();

        var msg = gameHandle.getTranslations().translateText("game.ap2.guess_it.again")
                .formatted(DARK_GREEN, BOLD);

        for (ServerPlayer player : PlayerLookup.level(world)) {
            Title.get(player).title(Component.empty(), msg.translateFor(player));
        }

        gameHandle.getScheduler().timeout(this::playSound, SOUND_DELAY_TICKS);
    }

    private void randomizePitch() {
        float keyOffset = Mth.clamp(12f, 0f, 24f);
        float key = random.nextFloat(keyOffset);

        pitch = (float) Math.pow(2, (key - keyOffset * 0.5) / keyOffset);
        pitch = Mth.clamp(pitch, 0.5f, 2f);
    }

    private void playSound() {
        for (ServerPlayer player : PlayerLookup.level(world)) {
            ServerPlayerAccess.playSoundToPlayer(player, correct, SoundSource.MASTER, 1f, pitch);
        }
    }

    private void stopSound() {
        var packet = new ClientboundStopSoundPacket(correct.location(), SoundSource.MASTER);

        for (ServerPlayer player : PlayerLookup.level(world)) {
            player.connection.send(packet);
        }
    }

    @Override
    public boolean shouldPlayBeginSound() {
        return false;
    }
}
