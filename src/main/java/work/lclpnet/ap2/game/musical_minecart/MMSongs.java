package work.lclpnet.ap2.game.musical_minecart;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.*;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.ds.IndexedSet;
import work.lclpnet.ap2.impl.util.UriUtil;
import work.lclpnet.ap2.impl.util.music.MapSongCache;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.notica.api.data.SongMeta;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MMSongs {

    public static final Identifier MUSICAL_MINECART_TAG = ArcadeParty.identifier("musical_minecart");
    private final SongManager songManager;
    private final Translations translations;
    private final Random random;
    private final Logger logger;
    private final Set<WeightedSong> songs = new IndexedSet<>();
    private final List<WeightedSong> queue = new ArrayList<>();
    private final SongCache cache = new MapSongCache();

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
        if (queue.isEmpty()) {
            populateQueue();
        }

        var weightedSong = queue.removeFirst();
        LoadableSong loadable = weightedSong.getRandomElement(random);

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

        String from = info.from();

        if (!from.isBlank()) {
            return translations.translateText("game.ap2.musical_minecart.format.from",
                    styled(name, YELLOW), styled(from, AQUA)).formatted(GREEN);
        }

        String originalAuthor = info.optMeta().map(SongInfo.Meta::originalBy).orElseGet(meta::originalAuthor);
        String author = info.optMeta().map(SongInfo.Meta::author).orElseGet(meta::author);

        boolean hasAuthor = !author.isBlank();
        boolean hasOrigAuthor = !originalAuthor.isBlank();

        if (hasAuthor && hasOrigAuthor) {
            return translations.translateText("game.ap2.musical_minecart.format.by_original",
                    styled(name, YELLOW), styled(author, AQUA), styled(originalAuthor, DARK_AQUA)).formatted(GREEN);
        }

        if (!hasAuthor && !hasOrigAuthor) {
            var text = RootText.create().append(Text.literal(name).formatted(YELLOW));

            return TranslatedText.create(s -> text, translations::getLanguage);
        }

        return translations.translateText("game.ap2.musical_minecart.format.by",
                styled(name, YELLOW), styled(hasAuthor ? author : originalAuthor, AQUA)).formatted(GREEN);
    }

    public List<WeightedSong> getQueue() {
        return Collections.unmodifiableList(queue);
    }

    public Set<WeightedSong> getSongs() {
        return Collections.unmodifiableSet(songs);
    }
}
