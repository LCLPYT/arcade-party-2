package work.lclpnet.ap2.impl.util.music;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.SongInfo;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.lobby.game.asset.AssetPath;
import work.lclpnet.lobby.game.asset.AssetRepository;
import work.lclpnet.notica.util.SongUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AssetSongManager implements SongManager {

    private final AssetRepository assetRepository;
    private final Logger logger;
    private final Map<Identifier, Map<String, WeightedSong>> cache = new HashMap<>();

    public AssetSongManager(AssetRepository assetRepository, Logger logger) {
        this.assetRepository = assetRepository;
        this.logger = logger;
    }

    @NotNull
    private static Identifier getSongId(Path path) {
        Identifier noticaSongId = SongUtils.createSongId(path);
        
        return ApConstants.identifier(noticaSongId.getPath());
    }

    @Override
    public CompletableFuture<Set<WeightedSong>> getSongs(Identifier tag) {
        Map<String, WeightedSong> cachedTag = cache.getOrDefault(tag, null);

        if (cachedTag != null) {
            return CompletableFuture.completedFuture(Set.copyOf(cachedTag.values()));
        }

        return CompletableFuture.supplyAsync(() -> getSongsSync(tag));
    }

    private Set<WeightedSong> getSongsSync(Identifier tag) {
        var songDefs = readSongDefinitions(tag);

        try {
            return readSongs(songDefs);
        } catch (IOException e) {
            logger.error("Failed to fetch songs for '{}'", tag, e);
            return Set.of();
        }
    }

    @VisibleForTesting
    public SetMultimap<AssetPath, SongConfig> readSongDefinitions(Identifier tag) {
        var dir = AssetPath.of(tag.getNamespace(), tag.getPath());
        var assetPath = dir.resolve("songs.json");

        try (var in = assetRepository.getStream(assetPath)) {
            String content = new String(in.resource().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);

            // json specifies: song -> [configs / variants]
            SetMultimap<AssetPath, SongConfig> songDefs = HashMultimap.create();
            readSongConfigs(json, dir, songDefs);

            return songDefs;
        } catch (IOException | JSONException e) {
            logger.error("Failed to fetch song definitions from '{}'", assetPath, e);
            return ImmutableSetMultimap.of();
        }
    }

    private Set<WeightedSong> readSongs(SetMultimap<AssetPath, SongConfig> songDefs) throws IOException {
        // read metadata of directories of specified songs
        Map<AssetPath, SongDirectoryMeta> directoryMeta = songDefs.keySet().stream()
                .filter(Objects::nonNull)
                .map(AssetPath::parent)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), this::readMeta));

        Set<WeightedSong> songs = new HashSet<>();

        for (AssetPath songPath : songDefs.keySet()) {
            if (songPath == null || songPath.isEmpty()) continue;

            // last segment is the song name (path/to/song.nbs -> song.nbs)
            String[] segments = songPath.segments();
            String songName = segments[segments.length - 1];

            SongDirectoryMeta meta = directoryMeta.getOrDefault(songPath.parent(), SongDirectoryMeta.EMPTY);
            SongInfo info = meta.info().getOrDefault(songName, SongInfo.EMPTY);

            // each song file can have several configs, representing different versions (or sections) of the song
            Set<SongConfig> songConfigs = songDefs.get(songPath);

            if (songConfigs.isEmpty()) continue;

            var song = getSong(songPath, songConfigs, info);

            songs.add(song);
        }

        return songs;
    }

    private SimpleWeightedSong getSong(AssetPath songPath, Collection<SongConfig> variants, SongInfo info) {
        String file = info.file();
        AssetPath finalSongPath = file != null ? songPath.resolveSibling(file) : songPath;

        Identifier songId = getSongId(finalSongPath.toPath().getFileName());
        Set<LoadableSong> loadableSongs = toLoadable(info, variants, finalSongPath, songId);

        return new SimpleWeightedSong(loadableSongs, songId);
    }

    private @NotNull Set<LoadableSong> toLoadable(SongInfo info, Collection<SongConfig> configs, AssetPath path, Identifier songId) {
        return configs.stream()
                .map(config -> config.toLoadable(path, assetRepository, songId, config.optOverride()
                        .map(override -> override.override(info.meta()))
                        .map(info::withMeta)
                        .orElse(info)))
                .collect(Collectors.toSet());
    }

    @VisibleForTesting
    public SongDirectoryMeta readMeta(AssetPath path) {
        var assetPath = path.resolve("meta.json");

        try (var in = assetRepository.getStream(assetPath)) {
            String content = new String(in.resource().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);

            Map<String, SongInfo> info = new HashMap<>();
            readSongInfo(json, info);

            return new SongDirectoryMeta(info);
        } catch (IOException | JSONException e) {
            logger.debug("Failed to song directory metadata at {} (it's not necessarily required)", assetPath, e);
            return SongDirectoryMeta.EMPTY;
        }
    }

    @Override
    public void cache(WeightedSong song, Identifier tag, String songName) {
        cache.computeIfAbsent(tag, _tag -> new HashMap<>()).put(songName, song);
    }

    @Override
    public CompletableFuture<Optional<WeightedSong>> getSong(Identifier tag, String songName) {
        var cachedTag = cache.getOrDefault(tag, null);

        if (cachedTag != null) {
            WeightedSong song = cachedTag.getOrDefault(songName, null);

            if (song != null) {
                return CompletableFuture.completedFuture(Optional.of(song));
            }
        }

        return CompletableFuture.supplyAsync(() -> getSongSync(tag, songName));
    }

    public @NotNull Optional<WeightedSong> getSongSync(Identifier tag, String song) {
        var dir = AssetPath.of(tag.getNamespace(), tag.getPath());
        SongDirectoryMeta meta = readMeta(dir);
        SongInfo info = meta.info().getOrDefault(song, SongInfo.EMPTY);

        // first, try to read song configs from songs.json
        var defs = readSongDefinitions(tag);

        var configs = defs.get(dir.resolve(song));

        // if no config was defined, use the default
        if (configs.isEmpty()) {
            configs = Set.of(SongConfig.DEFAULT);
        }

        var songPath = dir.resolve(song + ".nbs");

        return Optional.of(getSong(songPath, configs, info));
    }

    @VisibleForTesting
    public void readSongInfo(JSONObject json, Map<String, SongInfo> songInfo) {
        JSONObject infoObj = json.getJSONObject("info");

        for (String key : infoObj.keySet()) {
            Object val = infoObj.get(key);

            if (!(val instanceof JSONObject info)) continue;

            String license = info.optString("license", "").trim();
            String file = info.optString("file", key);

            var meta = SongInfo.Meta.fromJson(info);

            songInfo.put(key, new SongInfo(license, file, meta));
        }
    }

    @VisibleForTesting
    public void readSongConfigs(JSONObject json, AssetPath scope, SetMultimap<AssetPath, SongConfig> configs) {
        JSONArray songs = json.getJSONArray("songs");

        for (Object entry : songs) {
            if (!(entry instanceof JSONObject song)) continue;

            if (song.has("scope") && song.has("songs")) {
                AssetPath childScope = scope.resolve(song.getString("scope"));

                readSongConfigs(song, childScope, configs);
                continue;
            }

            String file = song.getString("file");
            var cfg = parseSongConfig(song);

            configs.put(scope.resolve(file), cfg);
        }
    }

    private @NotNull SongConfig parseSongConfig(JSONObject song) {
        float weight = song.optFloat("weight", 1.0f);
        var override = SongInfo.Meta.fromJson(song);

        return new SongConfig(weight, override);
    }


    /**
     * A song config represents a song (section) along with information about how to play the song.
     * @param weight The weight of the section when sampling from all sections of one song file (when choosing a random section of the same song file) (default=1.0).
     * @param override Optional meta override, e.g. to specify a different song title. Useful in combination with merge tags.
     */
    public record SongConfig(
            float weight,
            @Nullable SongInfo.Meta override
    ) {

        public static final SongConfig DEFAULT = new SongConfig(1.0f, null);

        public AssetPathLoadableSong toLoadable(AssetPath path, AssetRepository assetRepo, Identifier songId, SongInfo info) {
            return new AssetPathLoadableSong(path, assetRepo, songId, weight, info);
        }

        public Optional<SongInfo.Meta> optOverride() {
            return Optional.ofNullable(override);
        }
    }

    public record SongDirectoryMeta(Map<String, SongInfo> info) {
        public static final SongDirectoryMeta EMPTY = new SongDirectoryMeta(Map.of());
    }
}
