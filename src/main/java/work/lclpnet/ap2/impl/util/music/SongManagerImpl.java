package work.lclpnet.ap2.impl.util.music;

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
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.notica.util.SongUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SongManagerImpl implements SongManager {

    private final Path dir;
    private final SetMultimap<Identifier, LoadableSong> songs = HashMultimap.create();
    private final Set<Identifier> songIds = new HashSet<>();

    public SongManagerImpl(Path dir) {
        this.dir = dir;
    }

    @Override
    public Set<LoadableSong> getSongs(Identifier tag) {
        return songs.get(tag);
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

        SetMultimap<String, SongConfig> songConfigs = null;
        Map<String, Path> songFiles = new HashMap<>();

        try (var in = new TarArchiveInputStream(new XZCompressorInputStream(url.openStream()))) {
            TarArchiveEntry entry;

            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                Path fileName = Path.of(entry.getName()).getFileName();
                String str = fileName.toString();

                if (str.equals("config.json")) {
                    songConfigs = readConfig(in);
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

        if (songConfigs == null) {
            logger.warn("Song index is undefined, fallback to default values: volume=1.0, start=0");
            songConfigs = HashMultimap.create();
        }

        /*
        - each song file can have several configs, representing different versions (or sections) of the song
        - if a song does not have a config, the default values are used
         */

        for (var file : songFiles.entrySet()) {
            String name = file.getKey();
            Set<SongConfig> configs = songConfigs.get(name);

            if (configs.isEmpty()) {
                logger.debug("Song {} has no configuration, fallback to default values: volume=1.0, start=0", name);
                configs = Set.of(SongConfig.DEFAULT);
            }

            Path path = file.getValue();
            Identifier songId = reserveSongId(SongUtils.createSongId(path));

            for (SongConfig config : configs) {
                songs.put(tag, config.toLoadable(path, songId));
            }
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

    private static SetMultimap<String, SongConfig> readConfig(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);

        JSONObject json = new JSONObject(content);

        SetMultimap<String, SongConfig> index = HashMultimap.create();

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

            index.put(file, cfg);
        }

        return index;
    }

    private record SongConfig(float volume, int startTick, float weight) {
        public static final SongConfig DEFAULT = new SongConfig(1.0f, 0, 1.0f);

        public PathLoadableSong toLoadable(Path path, Identifier songId) {
            return new PathLoadableSong(path, songId, volume, startTick, weight);
        }
    }
}
