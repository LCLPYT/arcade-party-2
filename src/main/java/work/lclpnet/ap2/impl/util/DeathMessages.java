package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.GRAY;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DeathMessages {

    private static final String
            ELIMINATED = "ap2.game.eliminated",
            TEAM_ELIMINATED = "ap2.game.team_eliminated",
            KILLED_BY = "ap2.pvp.killed_by",
            SHOT_BY = "ap2.pvp.shot_by";
    private final TranslationService translations;

    public DeathMessages(TranslationService translations) {
        this.translations = translations;
    }

    private TranslatedText root(String key, Object... args) {
        return root(translations.translateText(key, args));
    }

    private TranslatedText root(TranslatedText text) {
        return text.formatted(GRAY);
    }

    private FormatWrapper wrap(PlayerEntity player) {
        return wrap(player.getNameForScoreboard());
    }

    private FormatWrapper wrap(Object obj) {
        return styled(obj, YELLOW);
    }

    public TranslatedText eliminated(ServerPlayerEntity player) {
        return root(ELIMINATED, wrap(player));
    }

    public TranslatedText killedBy(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        return root(KILLED_BY, wrap(victim), wrap(killer));
    }

    public TranslatedText shotBy(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        return root(SHOT_BY, victim, killer);
    }

    public TranslatedText eliminated(Team team) {
        return eliminated(team.getKey());
    }

    public TranslatedText eliminated(TeamKey key) {
        var displayName = translations.translateText(key.getTranslationKey()).formatted(key.colorFormat());

        return root(TEAM_ELIMINATED, displayName);
    }

    @NotNull
    public TranslatedText getDeathMessage(ServerPlayerEntity player, @Nullable DamageSource source) {
        if (source == null) return eliminated(player);

        Entity attacker = source.getAttacker();

        if (!(attacker instanceof ServerPlayerEntity killer)) return eliminated(player);

        Entity directSource = source.getSource();

        if (directSource instanceof ProjectileEntity && !(directSource instanceof SnowballEntity)) {
            return shotBy(player, killer);
        }

        return killedBy(player, killer);
    }
}
