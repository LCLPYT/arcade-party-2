package work.lclpnet.ap2.impl.music;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.music.ConfiguredSong;
import work.lclpnet.ap2.api.music.LoadableSong;
import work.lclpnet.ap2.api.music.SongCache;
import work.lclpnet.ap2.api.music.SongInfo;
import work.lclpnet.lobby.game.asset.AssetPath;
import work.lclpnet.lobby.game.asset.AssetRepository;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.util.ServerSongLoader;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class AssetPathLoadableSong implements LoadableSong {

    private final AssetPath path;
    private final AssetRepository assetRepo;
    private final Identifier id;
    private final float weight;
    private final SongInfo info;

    public AssetPathLoadableSong(AssetPath path, AssetRepository assetRepo, Identifier id, float weight, SongInfo info) {
        this.path = path;
        this.assetRepo = assetRepo;
        this.id = id;
        this.weight = weight;
        this.info = info;
    }

    @Override
    public CompletableFuture<ConfiguredSong> load(SongCache cache, Logger logger) {
        CheckedSong cached = cache.getCachedSong(path);

        if (cached != null) {
            return CompletableFuture.completedFuture(new ConfiguredSong(cached, info));
        }

        return CompletableFuture.supplyAsync(() -> {
            CheckedSong song;

            try (var res = assetRepo.getStream(path)) {
                song = ServerSongLoader.load(res.resource(), id);
            } catch (IOException e) {
                logger.error("Failed to load song {}", id, e);
                return null;
            }

            cache.cacheSong(path, song);

            return new ConfiguredSong(song, info);
        });
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public SongInfo getInfo() {
        return info;
    }
}
