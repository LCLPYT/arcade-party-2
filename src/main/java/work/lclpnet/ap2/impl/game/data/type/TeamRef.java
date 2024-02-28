package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Objects;

public class TeamRef implements SubjectRef {

    private final TeamKey key;
    private final TranslationService translations;

    public TeamRef(TeamKey key, TranslationService translations) {
        this.key = key;
        this.translations = translations;
    }

    public TeamKey getKey() {
        return key;
    }

    @Override
    public Text getNameFor(ServerPlayerEntity viewer) {
        return translations.translateText(viewer, key.getTranslationKey()).formatted(key.colorFormat());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamRef teamRef = (TeamRef) o;
        return Objects.equals(key, teamRef.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
