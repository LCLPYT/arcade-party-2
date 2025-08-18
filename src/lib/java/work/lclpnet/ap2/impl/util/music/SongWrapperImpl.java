package work.lclpnet.ap2.impl.util.music;

import work.lclpnet.ap2.api.util.music.SongWrapper;
import work.lclpnet.notica.api.SongHandle;

public class SongWrapperImpl implements SongWrapper {

    private SongHandle handle = null;

    @Override
    public void stop() {
        synchronized (this) {
            if (handle == null) return;

            handle.stop();
        }
    }

    public void setHandle(SongHandle handle) {
        synchronized (this) {
            this.handle = handle;
        }
    }
}
