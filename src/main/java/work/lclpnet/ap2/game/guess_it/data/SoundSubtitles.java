package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SoundSubtitles {

    private final Set<SoundEvent> soundEvents;

    public SoundSubtitles(Set<SoundEvent> soundEvents) {
        this.soundEvents = soundEvents;
    }

    public static CompletableFuture<SoundSubtitles> load() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadSync();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static SoundSubtitles loadSync() throws IOException {
        String content;

        try (InputStream in = SoundSubtitles.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (in == null) {
                throw new FileNotFoundException("Cannot find language file");
            }

            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        JSONObject json = new JSONObject(content);

        var sounds = loadSoundsFromJson(json);

        return new SoundSubtitles(sounds);
    }

    private static Set<SoundEvent> loadSoundsFromJson(JSONObject json) {
        Set<SoundEvent> soundEvents = new HashSet<>();
        var it = json.keys();

        String prefix = "subtitles.";

        while (it.hasNext()) {
            String key = it.next();

            if (!key.startsWith(prefix)) continue;

            String rest = key.substring(prefix.length());
            Identifier id = new Identifier(rest);
            SoundEvent soundEvent = Registries.SOUND_EVENT.get(id);

            if (soundEvent != null) {
                soundEvents.add(soundEvent);
            }
        }

        return soundEvents;
    }

    public Set<SoundEvent> getSoundEvents() {
        return soundEvents;
    }
}
