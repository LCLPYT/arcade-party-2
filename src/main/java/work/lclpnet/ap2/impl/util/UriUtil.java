package work.lclpnet.ap2.impl.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtil {

    public static final Pattern HTTP_S_PATTERN = Pattern.compile("(?:https?://.)?(?:www\\.)?[-a-zA-Z0-9@%._+~#=]{2,256}\\.[a-z]{2,6}\\b[-a-zA-Z0-9@:%_+.~#?&/=]*");

    public static List<URI> findUris(String src, int expectedMatches) {
        Matcher matcher = HTTP_S_PATTERN.matcher(src);
        List<URI> uris = new ArrayList<>(expectedMatches);

        while (matcher.find()) {
            String str = matcher.group(0);
            URI uri = URI.create(str);
            uris.add(uri);
        }

        return uris;
    }
}
