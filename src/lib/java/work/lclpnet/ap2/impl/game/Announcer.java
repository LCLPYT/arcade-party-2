package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.function.Supplier;

import static net.minecraft.ChatFormatting.AQUA;
import static net.minecraft.ChatFormatting.DARK_GREEN;

public class Announcer {

    private final Translations translations;
    private final Supplier<Iterable<ServerPlayer>> players;
    @Nullable
    private SoundEvent sound = SoundEvents.NOTE_BLOCK_PLING.value();
    private SoundSource category = SoundSource.RECORDS;
    private float volume = 0.5f;
    private float pitch = 0.5f;
    private int fadeInTicks = 5;
    private int stayTicks = 30;
    private int fadeOutTicks = 5;

    public Announcer(Translations translations, MinecraftServer server) {
        this(translations, () -> PlayerLookup.all(server));
    }

    public Announcer(Translations translations, Supplier<Iterable<ServerPlayer>> players) {
        this.translations = translations;
        this.players = players;
    }

    public Announcer withDefaults() {
        this.sound = SoundEvents.NOTE_BLOCK_PLING.value();
        this.category = SoundSource.RECORDS;
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

    public Announcer withSound(@Nullable SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.sound = sound;
        this.category = category == null ? SoundSource.NEUTRAL : category;
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
        for (ServerPlayer player : players.get()) {
            Component titleText = title != null ? title.translateFor(player) : Component.empty();
            Component subTitleText = subtitle != null ? subtitle.translateFor(player) : Component.empty();

            Title.get(player).title(titleText, subTitleText, fadeInTicks, stayTicks, fadeOutTicks);

            if (sound == null) continue;

            player.playNotifySound(sound, category, volume, pitch);
        }
    }
}
