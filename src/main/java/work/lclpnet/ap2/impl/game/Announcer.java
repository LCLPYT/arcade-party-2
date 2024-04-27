package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.AQUA;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class Announcer {

    private final TranslationService translations;
    private final MinecraftServer server;

    public Announcer(TranslationService translations, MinecraftServer server) {
        this.translations = translations;
        this.server = server;
    }

    public void announceSubtitle(String translationKey) {
        announce(null, translationKey, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);
    }

    public void announceSubtitle(String translationKey, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        announce(null, translationKey, sound, category, volume, pitch);
    }

    public void announce(@Nullable String titleKey, @Nullable String subtitleKey) {
        announce(titleKey, subtitleKey, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);
    }

    public void announce(@Nullable String titleKey, @Nullable String subtitleKey, SoundEvent sound,
                         SoundCategory category, float volume, float pitch) {

        TranslatedText title = titleKey != null ? translations.translateText(titleKey).formatted(AQUA) : null;
        TranslatedText subtitle = subtitleKey != null ? translations.translateText(subtitleKey).formatted(DARK_GREEN) : null;

        announce(title, subtitle, sound, category, volume, pitch);
    }

    public void announce(@Nullable TranslatedText title, @Nullable TranslatedText subtitle) {
        announce(title, subtitle, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);
    }

    public void announce(@Nullable TranslatedText title, @Nullable TranslatedText subtitle, SoundEvent sound,
                         SoundCategory category, float volume, float pitch) {

        SoundHelper.playSound(server, sound, category, volume, pitch);

        announceWithoutSound(title, subtitle);
    }

    public void announceWithoutSound(@Nullable String titleKey, @Nullable String subtitleKey) {
        announceWithoutSound(titleKey, subtitleKey, 5, 30, 5);
    }

    public void announceWithoutSound(@Nullable String titleKey, @Nullable String subtitleKey, int in, int stay, int out) {
        TranslatedText title = titleKey != null ? translations.translateText(titleKey).formatted(AQUA) : null;
        TranslatedText subtitle = subtitleKey != null ? translations.translateText(subtitleKey).formatted(DARK_GREEN) : null;

        announceWithoutSound(title, subtitle, in, stay, out);
    }

    public void announceWithoutSound(@Nullable TranslatedText title, @Nullable TranslatedText subtitle) {
        announceWithoutSound(title, subtitle, 5, 30, 5);
    }

    public void announceWithoutSound(@Nullable TranslatedText title, @Nullable TranslatedText subtitle, int in, int stay, int out) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            Text titleText = title != null ? title.translateFor(player) : Text.empty();
            Text subTitleText = subtitle != null ? subtitle.translateFor(player) : Text.empty();

            Title.get(player).title(titleText, subTitleText, in, stay, out);
        }
    }
}
