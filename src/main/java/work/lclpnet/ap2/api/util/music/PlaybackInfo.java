package work.lclpnet.ap2.api.util.music;

import work.lclpnet.notica.api.StereoMode;

public record PlaybackInfo(float volume, int startTick, StereoMode stereoMode) {
}
