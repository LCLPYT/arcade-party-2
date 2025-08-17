package work.lclpnet.ap2.api.util.music;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Optional;

public record SongInfo(String from, String license, @Nullable String file, @Nullable SongInfo.Meta meta) {

    public static final SongInfo EMPTY = new SongInfo("", "", null, null);

    public Optional<Meta> optMeta() {
        return Optional.ofNullable(meta);
    }

    public SongInfo withMeta(Meta meta) {
        return new SongInfo(from, license, file, meta);
    }

    public record Meta(@Nullable String title, @Nullable String author, @Nullable String originalBy, @Nullable String from) {

        public Meta override(@Nullable Meta parent) {
            if (parent == null) {
                return this;
            }

            return new Meta(
                    this.title != null ? this.title : parent.title,
                    this.author != null ? this.author : parent.author,
                    this.originalBy != null ? this.originalBy : parent.originalBy,
                    this.from != null ? this.from : parent.from
            );
        }

        public static @Nullable SongInfo.Meta fromJson(@Nullable JSONObject json) {
            if (json == null) return null;

            String title = json.optString("title", null);
            String author = json.optString("author", null);
            String originalBy = json.optString("original_by", null);
            String from = json.optString("from", null);

            return new Meta(title, author, originalBy, from);
        }
    }
}
