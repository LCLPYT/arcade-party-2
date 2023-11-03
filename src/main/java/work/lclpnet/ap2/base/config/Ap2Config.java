package work.lclpnet.ap2.base.config;

import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.config.json.JsonConfig;
import work.lclpnet.config.json.JsonConfigFactory;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class Ap2Config implements JsonConfig {

    public List<URI> mapsSource = List.of(URI.create("https://maps.lclpnet.work/release/"));

    public Ap2Config() {}

    public Ap2Config(JSONObject json) {
        if (json.has("maps_source")) {
            JSONArray order = json.getJSONArray("maps_source");

            var builder = ImmutableList.<URI>builder();

            for (Object obj : order) {
                if (!(obj instanceof String str)) continue;

                builder.add(stringToUri(str));
            }

            this.mapsSource = builder.build();
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        JSONArray order = new JSONArray();

        for (URI uri : mapsSource) {
            order.put(uriToString(uri));
        }

        json.put("maps_source", order);

        return json;
    }

    private static URI stringToUri(String str) {
        String source = str.replace('\\', '/');

        if (!source.endsWith("/")) {
            source += "/";
        }

        return URI.create(source);
    }

    private static String uriToString(URI uri) {
        if (uri.getHost() != null) {
            return uri.toString();
        }

        // local path
        Path current = Path.of("").toAbsolutePath();
        Path lobbyPath;

        if (uri.getScheme() == null) {
            // uri without scheme
            lobbyPath = Path.of(uri.toString()).toAbsolutePath();
        } else {
            // file:/// uri
            lobbyPath = Path.of(uri);
        }

        Path relativeLobbySource = current.relativize(lobbyPath);
        return relativeLobbySource.toString();
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
