package work.lclpnet.ap2.api.music;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.notica.api.StereoMode;

import java.util.Locale;
import java.util.Optional;

public record SongInfo(String license, @Nullable String file, SongInfo.Meta meta) {

    public static final SongInfo EMPTY = new SongInfo("", null, Meta.DEFAULT);

    public Optional<Meta> optMeta() {
        return Optional.ofNullable(meta);
    }

    public SongInfo withMeta(Meta meta) {
        return new SongInfo(license, file, meta);
    }

    public record Meta(@Nullable String title, @Nullable String author, @Nullable String originalBy,
                       @Nullable String from, Optional<Float> volume, Optional<Integer> startTick,
                       Optional<StereoMode> stereoMode) {

        public static Meta DEFAULT = new Meta(null, null, null, null, Optional.empty(),
                Optional.empty(), Optional.empty());

        public Meta withParent(@Nullable Meta parent) {
            if (parent == null) {
                return this;
            }

            return new Meta(
                    this.title != null ? this.title : parent.title,
                    this.author != null ? this.author : parent.author,
                    this.originalBy != null ? this.originalBy : parent.originalBy,
                    this.from != null ? this.from : parent.from,
                    this.volume.isPresent() ? this.volume : parent.volume,
                    this.startTick.isPresent() ? this.startTick : parent.startTick,
                    this.stereoMode.isPresent() ? this.stereoMode : parent.stereoMode
            );
        }

        public static @Nullable SongInfo.Meta fromJson(@Nullable JSONObject json) {
            if (json == null) return null;

            String title = json.optString("title", null);
            String author = json.optString("author", null);
            String originalBy = json.optString("original_by", null);
            String from = json.optString("from", null);
            var volume = json.has("volume") ? Optional.of(json.getFloat("volume")) : Optional.<Float>empty();
            var start = json.has("start") ? Optional.of(json.getInt("start")) : Optional.<Integer>empty();
            var stereoMode = json.has("stereo_mode")
                    ? Optional.of(parseStereoMode(json.optString("stereo_mode")))
                    : Optional.<StereoMode>empty();

            return new Meta(title, author, originalBy, from, volume, start, stereoMode);
        }

        private static StereoMode parseStereoMode(String str) {
            try {
                return StereoMode.valueOf(str.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return StereoMode.SPATIAL;
            }
        }
    }
}
