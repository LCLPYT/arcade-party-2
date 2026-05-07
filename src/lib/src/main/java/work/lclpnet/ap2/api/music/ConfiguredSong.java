package work.lclpnet.ap2.api.music;

import work.lclpnet.notica.api.CheckedSong;

public record ConfiguredSong(CheckedSong checkedSong, SongInfo info) {
}
