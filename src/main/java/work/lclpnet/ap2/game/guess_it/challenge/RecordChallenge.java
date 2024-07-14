package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemHelper;
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
    private Item correct = null;
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

        DynamicRegistryManager registryManager = world.getRegistryManager();
        ItemHelper.getJukeboxSong(correct, registryManager).ifPresent(song -> {
            SoundEvent sound = song.soundEvent().value();

            for (ServerPlayerEntity player : PlayerLookup.world(world)) {
                player.playSoundToPlayer(sound, SoundCategory.RECORDS, 0.5f, 1f);
            }
        });

        input.expectSelection(opts.stream()
                .map(item -> ItemHelper.getJukeboxSong(item, registryManager)
                        .map(JukeboxSong::description)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toArray(Text[]::new));
    }

    private Set<Item> getMusicDiscs() {
        return Registries.ITEM.stream()
                .filter(item -> item.getComponents().contains(DataComponentTypes.JUKEBOX_PLAYABLE))
                .collect(Collectors.toSet());
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        Text answer = ItemHelper.getJukeboxSong(this.correct, world.getRegistryManager())
                .map(JukeboxSong::description)
                .orElse(null);

        result.setCorrectAnswer(answer);
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    @Override
    public void destroy() {
        ItemHelper.getJukeboxSong(correct, world.getRegistryManager()).ifPresent(song -> {
            SoundEvent sound = song.soundEvent().value();
            StopSoundS2CPacket packet = new StopSoundS2CPacket(sound.getId(), SoundCategory.RECORDS);

            for (ServerPlayerEntity player : PlayerLookup.world(world)) {
                player.networkHandler.sendPacket(packet);
            }
        });
    }
}
