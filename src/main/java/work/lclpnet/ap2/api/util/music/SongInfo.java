package work.lclpnet.ap2.api.util.music;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Optional;

public record SongInfo(String from, String license, @Nullable SongInfo.Meta meta) {

    public static final SongInfo EMPTY = new SongInfo("", "", null);

    public Optional<Meta> optMeta() {
        return Optional.ofNullable(meta);
    }

    public SongInfo withMeta(Meta meta) {
        return new SongInfo(from, license, meta);
    }

    public record Meta(@Nullable String title, @Nullable String author, @Nullable String originalBy) {

        public Meta override(Meta parent) {
            return new Meta(
                    this.title != null ? this.title : parent.title,
                    this.author != null ? this.author : parent.author,
                    this.originalBy != null ? this.originalBy : parent.originalBy
            );
        }

        public static @Nullable SongInfo.Meta fromJson(@Nullable JSONObject json) {
            if (json == null) return null;

            String title = json.optString("title");
            String author = json.optString("author");
            String originalBy = json.optString("original_by");

            return new Meta(title, author, originalBy);
        }
    }
}
