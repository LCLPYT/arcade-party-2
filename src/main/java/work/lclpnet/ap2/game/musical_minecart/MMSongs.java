package work.lclpnet.ap2.game.musical_minecart;

import lombok.Getter;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.*;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.ds.IndexedSet;
import work.lclpnet.ap2.impl.util.UriUtil;
import work.lclpnet.ap2.impl.util.music.SimpleWeightedSong;
import work.lclpnet.ap2.impl.util.music.VoidSongCache;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.notica.api.data.SongMeta;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MMSongs {

    public static final Identifier MUSICAL_MINECART_TAG = ArcadeParty.identifier("musical_minecart");
    private final SongManager songManager;
    private final Translations translations;
    private final Random random;
    private final Logger logger;
    @Getter
    private final Set<WeightedSong> songs = new IndexedSet<>();
    @Getter
    private final List<WeightedSong> queue = new ArrayList<>();
    @Getter
    private final List<WeightedSong> priority = new ArrayList<>();
    private final SongCache cache = VoidSongCache.INSTANCE;

    public MMSongs(SongManager songManager, Translations translations, Random random, Logger logger) {
        this.songManager = songManager;
        this.translations = translations;
        this.random = random;
        this.logger = logger;
    }

    public void init() {
        Set<WeightedSong> songs = songManager.getSongs(MUSICAL_MINECART_TAG);

        this.songs.addAll(songs);
    }

    public CompletableFuture<ConfiguredSong> getNextSong() {
        WeightedSong weightedSong;

        if (!priority.isEmpty()) {
            weightedSong = priority.removeFirst();
        } else {
            if (queue.isEmpty()) {
                populateQueue();
            }

            weightedSong = queue.removeFirst();
        }

        LoadableSong loadable = weightedSong.getRandomElement(random);

        return loadSong(loadable);
    }

    private CompletableFuture<ConfiguredSong> loadSong(LoadableSong loadable) {
        return loadable.load(cache, logger);
    }

    private void populateQueue() {
        if (songs.isEmpty()) throw new IllegalStateException("There are no songs defined");

        queue.clear();
        queue.addAll(songs);
        Collections.shuffle(queue, random);
    }

    @Nullable
    public TextTranslatable getSongTitle(SongInfo info, SongMeta meta) {
        TranslatedText title = createSongTitle(info, meta);

        if (title == null) return null;

        String license = info.license();

        if (license.isBlank()) {
            return title;
        }

        return language -> title.translateTo(language).append(Text.literal(" ⚖").formatted(LIGHT_PURPLE).styled(style -> {
            List<URI> uris = UriUtil.findUris(license, 1);

            if (!uris.isEmpty()) {
                style = style.withClickEvent(new ClickEvent.OpenUrl(uris.getLast()));
            }

            return style.withHoverEvent(new HoverEvent.ShowText(Text.literal(license).formatted(GRAY)));
        }));
    }

    private @Nullable TranslatedText createSongTitle(SongInfo info, SongMeta meta) {
        String name = info.optMeta().map(SongInfo.Meta::title).orElseGet(meta::name);

        if (name.isBlank()) {
            return null;
        }

        String originalAuthor = info.optMeta().map(SongInfo.Meta::originalBy).orElseGet(meta::originalAuthor);
        String author = info.optMeta().map(SongInfo.Meta::author).orElseGet(meta::author);

        boolean hasAuthor = !author.isBlank();
        boolean hasOrigAuthor = !originalAuthor.isBlank();

        String from = info.from();

        if (!from.isBlank()) {
            if (hasAuthor && hasOrigAuthor) {
                // Song "X" from "Y" by "Z" (original by "W")
                return translations.translateText("game.ap2.musical_minecart.format.from_by_original",
                        styled(name, YELLOW), styled(from, AQUA), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
            }

            if (!hasAuthor && !hasOrigAuthor) {
                // Song "X" from "Y"
                return translations.translateText("game.ap2.musical_minecart.format.from",
                        styled(name, YELLOW), styled(from, AQUA)).formatted(GREEN);
            }

            // Song "X" from "Y" by "Z"
            return translations.translateText("game.ap2.musical_minecart.format.from_by",
                    styled(name, YELLOW), styled(from, AQUA), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
        }

        if (hasAuthor && hasOrigAuthor) {
            // Song "X" by "Y" (original by "Z")
            return translations.translateText("game.ap2.musical_minecart.format.by_original",
                    styled(name, YELLOW), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
        }

        if (!hasAuthor && !hasOrigAuthor) {
            // Song "X"
            var text = RootText.create().append(Text.literal(name).formatted(YELLOW));

            return TranslatedText.create(s -> text, translations::getLanguage);
        }

        // Song "X" by "Y"
        return translations.translateText("game.ap2.musical_minecart.format.by",
                styled(name, YELLOW), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
    }

    public Set<Identifier> getSongIds() {
        return songs.stream()
                .map(WeightedSong::getAllElements)
                .flatMap(Collection::stream)
                .map(LoadableSong::getId)
                .collect(Collectors.toSet());
    }

    public Optional<WeightedSong> getRandomSongById(Identifier id) {
        var matchingSongs = streamSongsById(id).collect(Collectors.toSet());

        return matchingSongs.isEmpty() ? Optional.empty() : Optional.of(new SimpleWeightedSong(matchingSongs));
    }

    public @NotNull Stream<LoadableSong> streamSongsById(Identifier id) {
        return songs.stream()
                .map(WeightedSong::getAllElements)
                .flatMap(Collection::stream)
                .filter(song -> id.equals(song.getId()));
    }

    public Optional<WeightedSong> getSongByIdAndTime(Identifier id, int startTick) {
        return streamSongsById(id)
                .filter(song -> song.getPlaybackInfo().startTick() == startTick)
                .findAny()
                .map(song -> new SimpleWeightedSong(Set.of(song)));
    }

    public void pushSong(WeightedSong song) {
        priority.addFirst(song);
    }

    public boolean hasPrioritySongs() {
        return !priority.isEmpty();
    }
}
