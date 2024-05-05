package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RecordChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(15);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final GuessItDisplay display;
    private MusicDiscItem correct = null;
    private int correctOption = -1;

    public RecordChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, GuessItDisplay display) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.display = display;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_GUESS;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        TranslationService translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.music_disc"));

        var discs = getMusicDiscs();
        var opts = OptionMaker.createOptions(discs, 4, random);

        correctOption = random.nextInt(opts.size());
        correct = opts.get(correctOption);

        display.displayItem(new ItemStack(correct));

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.playSoundToPlayer(correct.getSound(), SoundCategory.RECORDS, 0.5f, 1f);
        }

        input.expectSelection(opts.stream()
                .map(MusicDiscItem::getDescription)
                .toArray(Text[]::new));
    }

    private Set<MusicDiscItem> getMusicDiscs() {
        return Registries.ITEM.getEntryList(ItemTags.MUSIC_DISCS)
                .orElseThrow().stream()
                .map(RegistryEntry::value)
                .map(item -> item instanceof MusicDiscItem mdi ? mdi : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(correct.getDescription());
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    @Override
    public void destroy() {
        StopSoundS2CPacket packet = new StopSoundS2CPacket(correct.getSound().getId(), SoundCategory.RECORDS);

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.networkHandler.sendPacket(packet);
        }
    }
}
