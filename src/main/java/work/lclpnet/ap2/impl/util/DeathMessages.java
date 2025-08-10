package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.core.hook.PlayerDeathMessageCallback;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.Arrays;

import static net.minecraft.util.Formatting.GRAY;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DeathMessages {

    private static final String
            ELIMINATED = "ap2.game.eliminated",
            TEAM_ELIMINATED = "ap2.game.team_eliminated",
            KILLED_BY = "ap2.pvp.killed_by",
            SHOT_BY = "ap2.pvp.shot_by";

    private final Translations translations;

    public DeathMessages(Translations translations) {
        this.translations = translations;
    }

    public TranslatedText root(String key, Object... args) {
        return root(translations.translateText(key, args));
    }

    public TranslatedText root(TranslatedText text) {
        return text.formatted(GRAY);
    }

    public Object wrap(PlayerEntity player) {
        return wrap(player.getDisplayName());
    }

    public Object wrap(Object obj) {
        if (obj instanceof Text text) {
            Style style = text.getStyle();
            HoverEvent hoverEvent = style.getHoverEvent();

            // text with entity hover action indicates an entity display name
            // if the display name already has a color, keep it as is
            if (hoverEvent != null && hoverEvent.getAction() == HoverEvent.Action.SHOW_ENTITY && style.getColor() != null) {
                return text;
            }
        }

        return styled(obj, YELLOW);
    }

    public TranslatedText eliminated(ServerPlayerEntity player) {
        return root(ELIMINATED, wrap(player));
    }

    public TranslatedText killedBy(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        return root(KILLED_BY, wrap(victim), wrap(killer));
    }

    public TranslatedText shotBy(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        return root(SHOT_BY, wrap(victim), wrap(killer));
    }

    public TranslatedText eliminated(Team team) {
        return eliminated(team.key());
    }

    public TranslatedText eliminated(TeamKey key) {
        var displayName = translations.translateText(key.getTranslationKey())
                .styled(style -> style.withColor(key.color()));

        return root(TEAM_ELIMINATED, displayName);
    }

    @NotNull
    public TranslatedText getDeathMessage(ServerPlayerEntity player, @Nullable DamageSource source) {
        TextContent content = player.getDamageTracker().getDeathMessage().getContent();

        if (content instanceof TranslatableTextContent translated) {
            String key = translated.getKey();

            if (translations.getTranslator().hasTranslation("en_us", key)) {
                return root(key, Arrays.stream(translated.getArgs()).map(this::wrap).toArray());
            }
        }

        if (source == null) {
            return eliminated(player);
        }

        Entity attacker = source.getAttacker();

        if (!(attacker instanceof ServerPlayerEntity killer)) {
            return eliminated(player);
        }

        Entity directSource = source.getSource();

        if (directSource instanceof ProjectileEntity && !(directSource instanceof SnowballEntity)) {
            return shotBy(player, killer);
        }

        return killedBy(player, killer);
    }

    public void replaceVanillaDeathMessages(ServerWorld world, HookRegistrar hooks) {
        world.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES).set(false, world.getServer());

        hooks.registerHook(PlayerDeathMessageCallback.HOOK, (player, source, currentMsg) -> {
            MinecraftServer server = player.getServer();

            if (server == null) return currentMsg;

            TranslatedText msg = getDeathMessage(player, source);
            msg.sendTo(PlayerLookup.all(server));

            return msg.translateFor(player);  // shown in death screen, but not sent to chat because of the game rule
        });
    }
}
