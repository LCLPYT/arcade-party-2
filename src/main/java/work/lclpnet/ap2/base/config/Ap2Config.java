package work.lclpnet.ap2.base.config;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import net.minecraft.util.Identifier;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.game.musical_minecart.MMSongs;
import work.lclpnet.config.json.JsonConfig;
import work.lclpnet.config.json.JsonConfigFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class Ap2Config implements JsonConfig {

    public List<URI> mapsSource = List.of(URI.create("https://maps.lclpnet.work/release/"));
    public SetMultimap<Identifier, URI> songSources = HashMultimap.create();

    public Ap2Config() {
        setDefaults();
    }

    public Ap2Config(JSONObject json) {
        if (json.has("maps_source")) {
            this.mapsSource = readUriList(json, "maps_source");
        }

        if (json.has("song_sources")) {
            JSONObject songSources = json.getJSONObject("song_sources");

            for (String key : songSources.keySet()) {
                Identifier tag = Identifier.tryParse(key);

                if (tag == null) continue;

                List<URI> uris = readUriList(songSources, key);

                this.songSources.putAll(tag, uris);
            }
        }

        setDefaults();
    }

    private void setDefaults() {
        // set defaults that cannot be set in the initializer
        if (!songSources.containsKey(MMSongs.MUSICAL_MINECART_TAG) || songSources.get(MMSongs.MUSICAL_MINECART_TAG).isEmpty()) {
            try {
                URL url = new URL("https://lclpnet.work/dl/ap2-songs");
                songSources.put(MMSongs.MUSICAL_MINECART_TAG, url.toURI());
            } catch (MalformedURLException | URISyntaxException err) {
                ArcadeParty.logger.error("Failed to set default song source", err);
            }
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        writeUriList(json, "maps_source", mapsSource);

        JSONObject songSources = new JSONObject();

        for (Identifier tag : this.songSources.keySet()) {
            writeUriList(songSources, tag.toString(), this.songSources.get(tag));
        }

        json.put("song_sources", songSources);

        return json;
    }

    private static void writeUriList(JSONObject json, String key, Collection<? extends URI> uriList) {
        JSONArray order = new JSONArray();

        for (URI uri : uriList) {
            order.put(uriToString(uri));
        }

        json.put(key, order);
    }

    private static List<URI> readUriList(JSONObject json, String key) {
        JSONArray order = json.getJSONArray(key);

        var builder = ImmutableList.<URI>builder();

        for (Object obj : order) {
            if (!(obj instanceof String str)) continue;

            builder.add(stringToUri(str));
        }

        return builder.build();
    }

    private static URI stringToUri(String str) {
        String source = str.replace('\\', '/');

        if (!source.endsWith("/")) {
            source += "/";
        }

        URI uri = URI.create(source);

        if (uri.getHost() != null) {
            return uri;
        }

        // uri is local path
        Path path = uri.getScheme() != null ? Path.of(uri) : Path.of(uri.getPath());

        return path.toUri();
    }

    private static String uriToString(URI uri) {
        if (uri.getHost() != null) {
            return uri.toString();
        }

        // local path
        Path current = Path.of("").toAbsolutePath();
        Path sourcePath;

        if (uri.getScheme() == null) {
            // uri without scheme
            sourcePath = Path.of(uri.toString()).toAbsolutePath();
        } else {
            // file:/// uri
            sourcePath = Path.of(uri);
        }

        Path relativeSourceSource = current.relativize(sourcePath);
        return relativeSourceSource.toString();
    }

    public static final JsonConfigFactory<Ap2Config> FACTORY = new JsonConfigFactory<>() {
        @Override
        public Ap2Config createDefaultConfig() {
            return new Ap2Config();
        }

        @Override
        public Ap2Config createConfig(JSONObject json) {
            return new Ap2Config(json);
        }
    };
}
