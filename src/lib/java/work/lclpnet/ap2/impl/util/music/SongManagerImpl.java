package work.lclpnet.ap2.impl.util.music;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import net.minecraft.util.Identifier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.util.music.*;
import work.lclpnet.ap2.impl.ds.IndexedSet;
import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.util.SongUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SongManagerImpl implements SongManager {

    private final Path dir;
    private final Logger logger;
    private final Map<Identifier, BiMap<Identifier, WeightedSong>> songByTag = new HashMap<>();

    public SongManagerImpl(Path dir, Logger logger) {
        this.dir = dir;
        this.logger = logger;
    }

    @Override
    public Set<WeightedSong> getSongs(Identifier tag) {
        var songs = songByTag.get(tag);

        if (songs == null) {
            return Set.of();
        }

        IndexedSet<WeightedSong> orderedSongs = new IndexedSet<>();

        songs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEachOrdered(orderedSongs::add);

        return orderedSongs;
    }

    @Override
    public Optional<WeightedSong> getSong(Identifier tag, Identifier id) {
        var songs = songByTag.get(tag);

        if (songs == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(songs.get(id));
    }

    public void loadBundleSync(Identifier tag, URI uri, int index) throws IOException {
        Map<String, Path> songFiles = new HashMap<>();

        BundleConfig bundleConfig = installBundle(tag, uri, index, songFiles);

        if (bundleConfig == null) {
            logger.debug("Song index is undefined, fallback to default values: volume=1.0, start=0");
            bundleConfig = new BundleConfig(HashMultimap.create(), new HashMap<>());
        }

        loadInstalledBundle(tag, logger, bundleConfig, songFiles);
    }

    private @Nullable BundleConfig installBundle(Identifier tag, URI uri, int index, Map<String, Path> songFiles) throws IOException {
        URL url = uri.toURL();

        Path dir = this.dir.resolve(tag.getNamespace()).resolve(tag.getPath()).resolve(String.valueOf(index));

        if (!Files.exists(dir)) {
            synchronized (this) {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            }
        }

        /*
        - assume tar.xz file
        - every tar entry file name is flattened
        - if the file is a song, extract it
        - if the file is called "config.json" read the config
         */

        BundleConfig bundleConfig = null;

        try (var in = new TarArchiveInputStream(new XZCompressorInputStream(url.openStream()))) {
            TarArchiveEntry entry;

            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                Path fileName = Path.of(entry.getName()).getFileName();
                String str = fileName.toString();

                if (str.equals("config.json")) {
                    bundleConfig = readConfig(in);
                    continue;
                }

                // check if the file is a song
                if (!str.endsWith(".nbs")) continue;

                Path file = dir.resolve(fileName);

                Files.deleteIfExists(file);

                // reserve file
                if (songFiles.containsKey(str)) {
                    logger.warn("Duplicate song {}, skipping it", file);
                    continue;
                }

                songFiles.put(str, file);

                Files.copy(in, file);
            }
        }
        return bundleConfig;
    }

    private void loadInstalledBundle(Identifier tag, Logger logger, BundleConfig bundleConfig, Map<String, Path> songFiles) {
        /*
        - each song file can have several configs, representing different versions (or sections) of the song
        - if a song does not have a config, the default values are used
         */

        SetMultimap<String, SongConfig> songConfigs = bundleConfig.songs();
        Map<String, SongInfo> songInfos = bundleConfig.info();

        var songs = songByTag.computeIfAbsent(tag, t -> HashBiMap.create());

        for (var file : songFiles.entrySet()) {
            String name = file.getKey();
            Set<SongConfig> configs = songConfigs.get(name);

            if (configs.isEmpty()) {
                logger.debug("Song {} has no configuration, fallback to default values: volume=1.0, start=0", name);
                configs = Set.of(SongConfig.DEFAULT);
            }

            SongInfo info = songInfos.getOrDefault(name, SongInfo.EMPTY);

            loadBundleFile(file.getValue(), configs, info, songs);
        }
    }

    private void loadBundleFile(Path path, Set<SongConfig> configs, SongInfo info, BiMap<Identifier, WeightedSong> songs) {
        /*
        - a song file may contain multiple song groups, defined by their "merge tags"
        - each merge tag is treated as a separate song file
        - merge tags may also be defined across multiple song files
        - merge tags must be valid identifier paths: [a-z0-9/._-]+
         */

        for (var entry : groupedSongs(configs).entrySet()) {
            Identifier songId = getSongId(entry.getKey(), path);

            Set<LoadableSong> loadableSongs = new HashSet<>();

            for (SongConfig config : entry.getValue()) {
                var songInfo = config.optOverride()
                        .map(override -> override.override(info.meta()))
                        .map(info::withMeta)
                        .orElse(info);

                loadableSongs.add(config.toLoadable(path, songId, songInfo));
            }

            songs.compute(songId, (id, prev) -> {
                if (prev == null) {
                    return new SimpleWeightedSong(loadableSongs);
                }

                var combinedSongs = new HashSet<>(prev.getAllElements());
                combinedSongs.addAll(loadableSongs);

                return new SimpleWeightedSong(combinedSongs);
            });
        }
    }

    private @NotNull Identifier getSongId(String group, Path path) {
        if (group.isBlank()) {
            return getSongId(path);
        }

        return ApConstants.identifier(group);
    }

    private Map<String, List<SongConfig>> groupedSongs(Set<SongConfig> configs) {
        return configs.stream()
                .collect(Collectors.groupingBy(cfg -> cfg.optMergeTag().orElse("")));
    }

    @NotNull
    private static Identifier getSongId(Path path) {
        Identifier noticaSongId = SongUtils.createSongId(path);
        return ApConstants.identifier(noticaSongId.getPath());
    }

    private BundleConfig readConfig(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);

        JSONObject json = new JSONObject(content);

        SetMultimap<String, SongConfig> configs = HashMultimap.create();

        if (json.has("songs")) {
            readSongConfigs(json, configs);
        }

        Map<String, SongInfo> songInfo = new HashMap<>();

        if (json.has("info")) {
            readSongInfos(json, songInfo);
        }

        return new BundleConfig(configs, songInfo);
    }

    private void readSongInfos(JSONObject json, Map<String, SongInfo> songInfo) {
        JSONObject infoObj = json.getJSONObject("info");

        for (String key : infoObj.keySet()) {
            Object val = infoObj.get(key);

            if (!(val instanceof JSONObject info)) continue;

            String from = info.optString("from", "").trim();
            String license = info.optString("license", "").trim();

            songInfo.put(key, new SongInfo(from, license, null));
        }
    }

    private void readSongConfigs(JSONObject json, SetMultimap<String, SongConfig> configs) {
        JSONArray songs = json.getJSONArray("songs");

        for (Object entry : songs) {
            if (!(entry instanceof JSONObject song)) continue;

            String file = song.getString("file");
            var cfg = parseSongConfig(song);

            configs.put(file, cfg);
        }
    }

    private @NotNull SongConfig parseSongConfig(JSONObject song) {
        float volume = song.optFloat("volume", 1.0f);
        int startTick = song.optInt("start", 0);
        float weight = song.optFloat("weight", 1.0f);
        String mergeTag = song.optString("merge_tag", null);
        StereoMode stereoMode = parseStereoMode(song.optString("stereo_mode", StereoMode.SPATIAL.name()));
        var override = SongInfo.Meta.fromJson(song.optJSONObject("override"));

        var playbackInfo = new PlaybackInfo(volume, startTick, stereoMode);

        return new SongConfig(playbackInfo, weight, mergeTag, override);
    }

    private StereoMode parseStereoMode(String str) {
        try {
            return StereoMode.valueOf(str.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid stereo mode: {}", str, e);
            return StereoMode.SPATIAL;
        }
    }

    private record BundleConfig(SetMultimap<String, SongConfig> songs, Map<String, SongInfo> info) {}

    /**
     * A song config represents a song (section) along with information about how to play the song.
     * @param playbackInfo Information for the song playback.
     * @param weight The weight of the section when sampling from all sections of one song file (when choosing a random section of the same song file) (default=1.0).
     * @param mergeTag An optional tag to treat different song sections as entirely different songs. I.e. groups of the same tag are treated as standalone song file.
     * @param override Optional meta override, e.g. to specify a different song title. Useful in combination with merge tags.
     */
    public record SongConfig(
            PlaybackInfo playbackInfo,
            float weight,
            @Nullable String mergeTag,
            @Nullable SongInfo.Meta override
    ) {

        public static final SongConfig DEFAULT = new SongConfig(new PlaybackInfo(1.0f, 0, StereoMode.SPATIAL),
                1.0f, null, null);

        public PathLoadableSong toLoadable(Path path, Identifier songId, SongInfo info) {
            return new PathLoadableSong(path, songId, playbackInfo, weight, info);
        }

        public Optional<String> optMergeTag() {
            return Optional.ofNullable(mergeTag);
        }

        public Optional<SongInfo.Meta> optOverride() {
            return Optional.ofNullable(override);
        }
    }
}
