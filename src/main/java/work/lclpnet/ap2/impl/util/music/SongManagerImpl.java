package work.lclpnet.ap2.impl.util.music;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import net.minecraft.util.Identifier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.SongInfo;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.notica.util.SongUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SongManagerImpl implements SongManager {

    private final Path dir;
    private final Map<Identifier, BiMap<Identifier, WeightedSong>> songByTag = new HashMap<>();
    private final Set<Identifier> songIds = new HashSet<>();

    public SongManagerImpl(Path dir) {
        this.dir = dir;
    }

    @Override
    public Set<WeightedSong> getSongs(Identifier tag) {
        var songs = songByTag.get(tag);

        if (songs == null) {
            return Set.of();
        }

        return songs.values();
    }

    @Override
    public Optional<WeightedSong> getSong(Identifier tag, Identifier id) {
        var songs = songByTag.get(tag);

        if (songs == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(songs.get(id));
    }

    public void loadBundleSync(Identifier tag, URI uri, int index, Logger logger) throws IOException {
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
        Map<String, Path> songFiles = new HashMap<>();

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

        if (bundleConfig == null) {
            logger.debug("Song index is undefined, fallback to default values: volume=1.0, start=0");
            bundleConfig = new BundleConfig(HashMultimap.create(), new HashMap<>());
        }

        /*
        - each song file can have several configs, representing different versions (or sections) of the song
        - if a song does not have a config, the default values are used
         */

        var songConfigs = bundleConfig.songs();
        var songInfos = bundleConfig.info();

        for (var file : songFiles.entrySet()) {
            String name = file.getKey();
            Set<SongConfig> configs = songConfigs.get(name);

            if (configs.isEmpty()) {
                logger.debug("Song {} has no configuration, fallback to default values: volume=1.0, start=0", name);
                configs = Set.of(SongConfig.DEFAULT);
            }

            SongInfo info = songInfos.getOrDefault(name, SongInfo.EMPTY);

            Path path = file.getValue();
            Identifier songId = reserveSongId(SongUtils.createSongId(path));

            SetMultimap<Identifier, LoadableSong> songsById = HashMultimap.create();

            for (SongConfig config : configs) {
                songsById.put(songId, config.toLoadable(path, songId, info));
            }

            BiMap<Identifier, WeightedSong> songs = HashBiMap.create(configs.size());

            for (Identifier id : songsById.keySet()) {
                Set<LoadableSong> songsWithId = songsById.get(id);

                if (songsWithId.isEmpty()) continue;

                SimpleWeightedSong weightedSong = new SimpleWeightedSong(songsWithId);
                songs.put(id, weightedSong);
            }

            songByTag.put(tag, songs);
        }
    }

    private synchronized Identifier reserveSongId(Identifier base) {
        Identifier id = generateUniqueSongId(base);
        songIds.add(id);
        return id;
    }

    private Identifier generateUniqueSongId(Identifier base) {
        if (!songIds.contains(base)) {
            return base;
        }

        final int maxTries = 100;

        for (int i = 1; i < maxTries; i++) {
            Identifier suffixed = base.withSuffixedPath("_" + i);

            if (!songIds.contains(suffixed)) {
                return suffixed;
            }
        }

        throw new IllegalStateException("generateUniqueSongId: Maximum tries exceeded");
    }

    private static BundleConfig readConfig(InputStream in) throws IOException {
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

    private static void readSongInfos(JSONObject json, Map<String, SongInfo> songInfo) {
        JSONObject infoObj = json.getJSONObject("info");

        for (String key : infoObj.keySet()) {
            Object val = infoObj.get(key);

            if (!(val instanceof JSONObject info)) continue;

            String from = null;

            if (info.has("from")) {
                from = info.getString("from").trim();
            }

            songInfo.put(key, new SongInfo(from));
        }
    }

    private static void readSongConfigs(JSONObject json, SetMultimap<String, SongConfig> configs) {
        JSONArray songs = json.getJSONArray("songs");

        for (Object entry : songs) {
            if (!(entry instanceof JSONObject song)) continue;

            String file = song.getString("file");

            boolean hasVolume = song.has("volume");
            boolean hasStart = song.has("start");
            boolean hasWeight = song.has("weight");

            SongConfig cfg;

            if (!hasVolume && !hasStart && !hasWeight) {
                cfg = SongConfig.DEFAULT;
            } else {
                float volume = hasVolume ? song.getFloat("volume") : 1.0f;
                int startTick = hasStart ? song.getInt("start") : 0;
                float weight = hasWeight ? song.getFloat("weight") : 1.0f;

                cfg = new SongConfig(volume, startTick, weight);
            }

            configs.put(file, cfg);
        }
    }

    private record BundleConfig(SetMultimap<String, SongConfig> songs, Map<String, SongInfo> info) {}

    private record SongConfig(float volume, int startTick, float weight) {
        public static final SongConfig DEFAULT = new SongConfig(1.0f, 0, 1.0f);

        public PathLoadableSong toLoadable(Path path, Identifier songId, SongInfo info) {
            return new PathLoadableSong(path, songId, volume, startTick, weight, info);
        }
    }
}
