package work.lclpnet.ap2.impl.music;

import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.music.*;
import work.lclpnet.ap2.impl.util.UriUtil;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.gaco.ds.queue.JsonFileQueuePersistence;
import work.lclpnet.gaco.ds.queue.QueuePersistence;
import work.lclpnet.gaco.ds.queue.SeamlessQueue;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.notica.api.data.LoopOverride;
import work.lclpnet.notica.api.data.SongMeta;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.floor;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.impl.util.TranslationUtil.transformText;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class SongHandler {

    public static final float MUSIC_VOLUME = 0.75f;
    public static final float MARGIN_PERCENT = 0.35f;

    private final SongManager songManager;
    private final Translations translations;
    private final Random random;
    private final Logger logger;
    private final QueuePersistence<Identifier> queuePersistence;
    @Getter
    private final Set<WeightedSong> songs = new IndexedSet<>();
    private final Map<Identifier, WeightedSong> songsById = new HashMap<>();
    @Getter
    private final List<WeightedSong> priority = new ArrayList<>();
    private final SongCache cache = VoidSongCache.INSTANCE;
    @Getter
    private SeamlessQueue<WeightedSong> queue = null;

    public SongHandler(MiniGameHandle handle, Random random) {
        this(handle.getSongManager(), handle.getTranslations(), random, handle.getLogger(),
                JsonFileQueuePersistence.create(ApConstants.ID, handle.getGameInfo().identifier("song_queue"),
                        Identifier.CODEC, handle.getLogger()));
    }

    public SongHandler(SongManager songManager, Translations translations, Random random, Logger logger, QueuePersistence<Identifier> queuePersistence) {
        this.songManager = songManager;
        this.translations = translations;
        this.random = random;
        this.logger = logger;
        this.queuePersistence = queuePersistence;
    }

    public CompletableFuture<Void> loadSongs(Identifier tag) {
        return CompletableFuture.runAsync(() -> {
            var songsFuture = songManager.getSongs(tag);
            var restored = queuePersistence.restore();
            var songs = songsFuture.join();

            this.songs.clear();
            this.songs.addAll(songs);

            songsById.clear();
            songsById.putAll(songs.stream()
                    .collect(Collectors.toMap(WeightedSong::getSongId, Function.identity())));

            int margin = (int) floor(songs.size() * MARGIN_PERCENT);

            queue = new SeamlessQueue<>(songs, random, margin, restored.map(songsById::get));
        });
    }

    public CompletableFuture<ConfiguredSong> loadNextSong() {
        WeightedSong weightedSong;

        if (!priority.isEmpty()) {
            weightedSong = priority.removeFirst();
        } else {
            weightedSong = queue.next();
        }

        LoadableSong loadable = weightedSong.getRandomElement(random);

        return loadSong(loadable);
    }

    public void pushSongHistory(ConfiguredSong song) {
        Identifier id = song.checkedSong().id();
        WeightedSong weightedSong = songsById.get(id);

        if (weightedSong == null) {
            logger.error("Cannot push unknown song with id '{}'", id);
            return;
        }

        queue.pushElement(weightedSong);

        persistQueue();
    }

    private CompletableFuture<ConfiguredSong> loadSong(LoadableSong loadable) {
        return loadable.load(cache, logger);
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
        Optional<SongInfo.Meta> optMeta = info.optMeta();

        String name = optMeta.map(SongInfo.Meta::title).orElseGet(meta::name);

        if (name.isBlank()) {
            return null;
        }

        String originalAuthor = optMeta.map(SongInfo.Meta::originalBy).orElseGet(meta::originalAuthor);
        String author = optMeta.map(SongInfo.Meta::author).orElseGet(meta::author);

        boolean hasAuthor = !author.isBlank();
        boolean hasOrigAuthor = !originalAuthor.isBlank();

        String from = optMeta.map(SongInfo.Meta::from).orElse("");

        if (!from.isBlank()) {
            if (hasAuthor && hasOrigAuthor) {
                // Song "X" from "Y" by "Z" (original by "W")
                return translations.translateText("ap2.music.format.from_by_original",
                        styled(name, YELLOW), styled(from, AQUA), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
            }

            if (!hasAuthor && !hasOrigAuthor) {
                // Song "X" from "Y"
                return translations.translateText("ap2.music.format.from",
                        styled(name, YELLOW), styled(from, AQUA)).formatted(GREEN);
            }

            // Song "X" from "Y" by "Z"
            return translations.translateText("ap2.music.format.from_by",
                    styled(name, YELLOW), styled(from, AQUA), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
        }

        if (hasAuthor && hasOrigAuthor) {
            // Song "X" by "Y" (original by "Z")
            return translations.translateText("ap2.music.format.by_original",
                    styled(name, YELLOW), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
        }

        if (!hasAuthor && !hasOrigAuthor) {
            // Song "X"
            var text = RootText.create().append(Text.literal(name).formatted(YELLOW));

            return TranslatedText.create(s -> text, translations::getLanguage);
        }

        // Song "X" by "Y"
        return translations.translateText("ap2.music.format.by",
                styled(name, YELLOW), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
    }

    public @Nullable TranslatedText nowPlayingText(ConfiguredSong configuredSong) {
        TextTranslatable title = getSongTitle(configuredSong.info(), configuredSong.checkedSong().song().metaData());

        if (title == null) return null;

        return transformText(
                translations.translateText("ap2.music.now_playing", title),
                text -> Text.literal("🎵 ").append(text).formatted(GREEN),
                translations
        );
    }

    public SongWrapper play(ConfiguredSong song, MinecraftServer server, int startTick, boolean sendText, boolean noLoop) {
        LoopOverride loop = LoopOverride.DEFAULT;

        if (noLoop) {
            loop = loop.withEnabled(false);
        }

        var songWrapper = MusicHelper.playSong(song, MUSIC_VOLUME, loop, startTick, server);

        if (!sendText) return songWrapper;

        TranslatedText nowPlaying = nowPlayingText(song);

        if (nowPlaying != null) {
            nowPlaying.sendTo(PlayerLookup.all(server));
        }

        pushSongHistory(song);

        return songWrapper;
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

        return matchingSongs.isEmpty() ? Optional.empty() : Optional.of(new SimpleWeightedSong(matchingSongs, id));
    }

    public @NotNull Stream<LoadableSong> streamSongsById(Identifier id) {
        return songs.stream()
                .map(WeightedSong::getAllElements)
                .flatMap(Collection::stream)
                .filter(song -> id.equals(song.getId()));
    }

    public Optional<WeightedSong> getSongByIdAndTime(Identifier id, int startTick) {
        return streamSongsById(id)
                .filter(song -> song.getInfo().meta().startTick().orElse(0) == startTick)
                .findAny()
                .map(song -> new SimpleWeightedSong(Set.of(song), id));
    }

    public void pushPrioritySong(WeightedSong song) {
        priority.addFirst(song);
    }

    public boolean hasPrioritySongs() {
        return !priority.isEmpty();
    }

    public void persistQueue() {
        var seamlessQueue = this.queue;

        if (seamlessQueue == null) return;

        CompletableFuture.runAsync(() -> queuePersistence.store(seamlessQueue.transfer().map(WeightedSong::getSongId)));
    }
}
