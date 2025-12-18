package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay;
import work.lclpnet.ap2.game.guess_it.util.OptionMaker;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class RecordChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(15);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final GuessItDisplay display;
    private Item correct = null;
    private int correctOption = -1;

    public RecordChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, GuessItDisplay display) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.display = display;
    }

    @Override
    public String id() {
        return "record";
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
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.music_disc"));

        List<Item> discs = getMusicDiscs();
        var opts = OptionMaker.createOptions(discs, 4, random);

        correctOption = random.nextInt(opts.size());
        correct = opts.get(correctOption);

        display.displayItem(new ItemStack(correct));

        RegistryAccess registryManager = world.registryAccess();
        ItemHelper.getJukeboxSong(correct, registryManager).ifPresent(song -> {
            SoundEvent sound = song.soundEvent().value();

            for (ServerPlayer player : PlayerLookup.world(world)) {
                player.playNotifySound(sound, SoundSource.RECORDS, 0.5f, 1f);
            }
        });

        input.expectSelection(opts.stream()
                .map(item -> ItemHelper.getJukeboxSong(item, registryManager)
                        .map(JukeboxSong::description)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toArray(Component[]::new));
    }

    private List<Item> getMusicDiscs() {
        return BuiltInRegistries.ITEM.listElements()
                .sorted(Comparator.comparing(reference -> reference.key().location()))
                .map(Holder.Reference::value)
                .filter(item -> item.components().has(DataComponents.JUKEBOX_PLAYABLE))
                .toList();
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        Component answer = ItemHelper.getJukeboxSong(this.correct, world.registryAccess())
                .map(JukeboxSong::description)
                .orElse(null);

        result.setCorrectAnswer(answer);
        result.grantIfCorrect(gameHandle.getParticipants(), correctOption, choices::getOption);
    }

    @Override
    public void destroy() {
        ItemHelper.getJukeboxSong(correct, world.registryAccess()).ifPresent(song -> {
            SoundEvent sound = song.soundEvent().value();
            ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(sound.location(), SoundSource.RECORDS);

            for (ServerPlayer player : PlayerLookup.world(world)) {
                player.connection.send(packet);
            }
        });
    }
}
