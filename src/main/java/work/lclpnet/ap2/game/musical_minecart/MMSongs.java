package work.lclpnet.ap2.game.musical_minecart;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.*;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.ap2.impl.util.music.MapSongCache;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.notica.api.data.SongMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MMSongs {

    public static final Identifier MUSICAL_MINECART_TAG = ArcadeParty.identifier("musical_minecart");
    private final SongManager songManager;
    private final TranslationService translations;
    private final Random random;
    private final Logger logger;
    private final Set<WeightedList<LoadableSong>> songs = new HashSet<>();
    private final List<WeightedList<LoadableSong>> queue = new ArrayList<>();
    private final SongCache cache = new MapSongCache();

    public MMSongs(SongManager songManager, TranslationService translations, Random random, Logger logger) {
        this.songManager = songManager;
        this.translations = translations;
        this.random = random;
        this.logger = logger;
    }

    public void init() {
        Set<LoadableSong> loadableSongs = songManager.getSongs(MUSICAL_MINECART_TAG);
        Multimap<Identifier, LoadableSong> byActualSong = ArrayListMultimap.create();

        // group by id
        for (LoadableSong loadable : loadableSongs) {
            byActualSong.put(loadable.getId(), loadable);
        }

        for (Identifier songId : byActualSong.keySet()) {
            var configuredSongs = byActualSong.get(songId);

            if (configuredSongs.isEmpty()) continue;

            WeightedList<LoadableSong> weighted = new WeightedList<>();

            for (LoadableSong loadable : configuredSongs) {
                weighted.add(loadable, loadable.getWeight());
            }

            songs.add(weighted);
        }
    }

    public CompletableFuture<ConfiguredSong> getNextSong() {
        if (queue.isEmpty()) {
            populateQueue();
        }

        var weightedSong = queue.remove(0);
        LoadableSong loadable = weightedSong.getRandomElement(random);

        if (loadable == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException("Failed to get random song"));
        }

        return loadable.load(cache, logger);
    }

    private void populateQueue() {
        if (songs.isEmpty()) throw new IllegalStateException("There are no songs defined");

        queue.clear();
        queue.addAll(songs);
        Collections.shuffle(queue, random);
    }

    @Nullable
    public Text getSongTitle(ServerPlayerEntity player, SongInfo info, SongMeta meta) {
        String name = meta.name();

        if (name.isBlank()) {
            return null;
        }

        String from = info.from();

        if (!from.isBlank()) {
            return translations.translateText(player, "game.ap2.musical_minecart.format.from",
                    styled(name, YELLOW), styled(from, AQUA)).formatted(GREEN);
        }

        String originalAuthor = meta.originalAuthor();
        String author = meta.author();

        boolean hasAuthor = !author.isBlank();
        boolean hasOrigAuthor = !originalAuthor.isBlank();

        if (hasAuthor && hasOrigAuthor) {
            return translations.translateText(player, "game.ap2.musical_minecart.format.by_original",
                    styled(name, YELLOW), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
        }

        if (!hasAuthor && !hasOrigAuthor) {
            return Text.literal(name).formatted(YELLOW);
        }

        return translations.translateText(player, "game.ap2.musical_minecart.format.by",
                styled(name, YELLOW), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
    }
}
