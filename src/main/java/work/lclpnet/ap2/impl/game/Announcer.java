package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.AQUA;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class Announcer {

    private final TranslationService translations;
    private final MinecraftServer server;
    @Nullable
    private SoundEvent sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
    private SoundCategory category = SoundCategory.RECORDS;
    private float volume = 0.5f;
    private float pitch = 0.5f;
    private int fadeInTicks = 5;
    private int stayTicks = 30;
    private int fadeOutTicks = 5;

    public Announcer(TranslationService translations, MinecraftServer server) {
        this.translations = translations;
        this.server = server;
    }

    public Announcer withDefaults() {
        this.sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        this.category = SoundCategory.RECORDS;
        this.volume = 0.5f;
        this.pitch = 0.5f;
        this.fadeInTicks = 5;
        this.stayTicks = 30;
        this.fadeOutTicks = 5;
        return this;
    }

    public Announcer silent() {
        this.sound = null;
        return this;
    }

    public Announcer withSound(@Nullable SoundEvent sound, SoundCategory category, float volume, float pitch) {
        this.sound = sound;
        this.category = category == null ? SoundCategory.NEUTRAL : category;
        this.volume = Math.max(0f, volume);
        this.pitch = Math.max(0.5f, Math.min(2f, pitch));
        return this;
    }

    public Announcer withTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        this.fadeInTicks = Math.max(0, fadeInTicks);
        this.stayTicks = Math.max(0, stayTicks);
        this.fadeOutTicks = Math.max(0, fadeOutTicks);
        return this;
    }

    public void announceSubtitle(String translationKey) {
        announce(null, translationKey);
    }

    public void announce(@Nullable String titleKey, @Nullable String subtitleKey) {
        TranslatedText title = titleKey != null ? translations.translateText(titleKey).formatted(AQUA) : null;
        TranslatedText subtitle = subtitleKey != null ? translations.translateText(subtitleKey).formatted(DARK_GREEN) : null;

        announce(title, subtitle);
    }

    public void announce(@Nullable TranslatedText title, @Nullable TranslatedText subtitle) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            Text titleText = title != null ? title.translateFor(player) : Text.empty();
            Text subTitleText = subtitle != null ? subtitle.translateFor(player) : Text.empty();

            Title.get(player).title(titleText, subTitleText, fadeInTicks, stayTicks, fadeOutTicks);

            if (sound == null) continue;

            player.playSound(sound, category, volume, pitch);
        }
    }
}
