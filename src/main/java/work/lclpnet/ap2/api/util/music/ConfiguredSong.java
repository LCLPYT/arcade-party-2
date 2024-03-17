package work.lclpnet.ap2.api.util.music;

import work.lclpnet.notica.api.CheckedSong;

public record ConfiguredSong(CheckedSong song, float volume, int startTick, float weight, SongInfo info) {
}
