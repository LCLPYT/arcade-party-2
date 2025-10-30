package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.BaseGameInstance;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.notica.network.NoticaNetworking;

import java.net.URI;
import java.util.function.Predicate;

import static java.lang.Math.max;
import static net.minecraft.util.Formatting.*;

public class Hints {


    private final Translations translations;
    private final MinecraftServer server;

    public Hints(MiniGameHandle gameHandle) {
        this(gameHandle.getTranslations(), gameHandle.getServer());
    }

    public Hints(Translations translations, MinecraftServer server) {
        this.translations = translations;
        this.server = server;
    }

    public void sendModHint(Mod mod) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (mod.installed().test(player)) continue;

            var modLabel = Text.literal(mod.name() + " ↗")
                    .styled(style -> style
                            .withColor(0x145ee8)
                            .withBold(false)
                            .withUnderline(true)
                            .withHoverEvent(new HoverEvent.ShowText(translations.translateText(player, "ap2.hint.click_open", mod.link().toString())))
                            .withClickEvent(new ClickEvent.OpenUrl(mod.link())));

            var sub = translations.translateText(player, "ap2.hint.mod", modLabel)
                    .formatted(YELLOW)
                    .styled(style -> style.withBold(false));

            var hint = translations.translateText(player, "ap2.hint", sub)
                    .formatted(RED, BOLD);

            player.sendMessage(hint);
            player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 0.5f, 0.5f);
        }
    }

    public void sendBeforeReady(BaseGameInstance gameInstance, Mod mod) {
        MiniGameHandle gameHandle = gameInstance.getGameHandle();

        gameHandle.getScheduler().timeout(() -> sendModHint(mod),
                max(0, gameInstance.getInitialDelay() - Ticks.seconds(3)));
    }

    public record Mod(String name, URI link, Predicate<ServerPlayerEntity> installed) {

        public static final Mod NOTICA = new Mod(
                "Notica",
                URI.create("https://modrinth.com/mod/notica"),
                player -> NoticaNetworking.getInstance().understandsProtocol(player)
        );
    }
}
