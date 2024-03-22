package work.lclpnet.ap2.api.util.music;

public record SongInfo(String from) {

    public static final SongInfo EMPTY = new SongInfo("");
}
