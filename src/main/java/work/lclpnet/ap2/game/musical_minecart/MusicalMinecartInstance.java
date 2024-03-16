package work.lclpnet.ap2.game.musical_minecart;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.notica.Notica;

import java.util.Random;

public class MusicalMinecartInstance extends EliminationGameInstance {

    private final MMSongs songManager;
    private final Random random = new Random();
    private BlockBox bounds = null;

    public MusicalMinecartInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        this.songManager = new MMSongs(gameHandle.getSongManager(), random, gameHandle.getLogger());
    }

    @Override
    protected void prepare() {
        bounds = MapUtil.readBox(getMap().requireProperty("bounds"));
        songManager.init();
    }

    @Override
    protected void ready() {
        nextSong();
    }

    private void nextSong() {
        songManager.getNextSong().thenAccept(this::playSong).whenComplete((res, err) -> {
            if (err == null) return;

            gameHandle.getLogger().error("Failed to load next song", err);
        });
    }

    private void playSong(ConfiguredSong config) {
        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        Notica notica = Notica.getInstance(server);
        notica.playSong(config.song(), config.volume(), config.startTick(), players);
    }
}
