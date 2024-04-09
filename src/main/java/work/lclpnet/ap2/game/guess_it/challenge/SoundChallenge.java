package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Random;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class SoundChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(14);
    private static final int SOUND_DELAY_TICKS = 30;
    private static final int REPEAT_DELAY_TICKS = Ticks.seconds(4);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final SoundSubtitles soundSubtitles;
    private SoundEvent correct = null;
    private float pitch = 1f;
    private int correctOption = -1;

    public SoundChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, SoundSubtitles soundSubtitles) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.soundSubtitles = soundSubtitles;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_LISTEN;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS + 2 * SOUND_DELAY_TICKS + REPEAT_DELAY_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        TranslationService translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.sound.guess"));

        var soundEvents = soundSubtitles.getSoundEvents();
        var soundOptions = OptionMaker.createOptions(soundEvents, 4, random);

        correctOption = random.nextInt(soundOptions.size());
        correct = soundOptions.get(correctOption);
        randomizePitch();

        var options = soundOptions.stream()
                .map(TextUtil::getVanillaName)
                .toArray(Text[]::new);

        input.expectSelection(options);

        gameHandle.getGameScheduler().timeout(this::playFirst, SOUND_DELAY_TICKS);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        stopSound();

        result.setCorrectAnswer(TextUtil.getVanillaName(correct));
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    private void playFirst() {
        playSound();

        gameHandle.getGameScheduler().timeout(this::prepareSecond, REPEAT_DELAY_TICKS);
    }

    private void prepareSecond() {
        stopSound();

        var msg = gameHandle.getTranslations().translateText("game.ap2.guess_it.again")
                .formatted(DARK_GREEN, BOLD);

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            Title.get(player).title(Text.empty(), msg.translateFor(player));
        }

        gameHandle.getGameScheduler().timeout(this::playSound, SOUND_DELAY_TICKS);
    }

    private void randomizePitch() {
        float keyOffset = MathHelper.clamp(12f, 0f, 24f);
        float key = random.nextFloat(keyOffset);

        pitch = (float) Math.pow(2, (key - keyOffset * 0.5) / keyOffset);
        pitch = MathHelper.clamp(pitch, 0.5f, 2f);
    }

    private void playSound() {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.playSound(correct, SoundCategory.MASTER, 1f, pitch);
        }
    }

    private void stopSound() {
        var packet = new StopSoundS2CPacket(correct.getId(), SoundCategory.MASTER);

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.networkHandler.sendPacket(packet);
        }
    }
}
