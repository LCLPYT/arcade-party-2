package work.lclpnet.ap2.api.config;

import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.config.json.JsonConfig;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class Ap2Config implements JsonConfig {

    public List<URI> mapsSource = List.of(URI.create("https://assets.lclpnet.work/release/maps/"));
    public List<URI> songsSource = List.of(URI.create("https://assets.lclpnet.work/release/songs/"));

    public Ap2Config() {}

    public Ap2Config(JSONObject json) {
        if (json.has("maps_source")) {
            this.mapsSource = readUriList(json, "maps_source");
        }

        if (json.has("songs_source")) {
            this.songsSource = readUriList(json, "songs_source");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        writeUriList(json, "maps_source", mapsSource);
        writeUriList(json, "songs_source", songsSource);

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
}
