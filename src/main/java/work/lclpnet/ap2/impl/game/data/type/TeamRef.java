package work.lclpnet.ap2.impl.game.data.type;

import lombok.Getter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.kibu.translate.Translations;

import java.util.Objects;

public class TeamRef implements SubjectRef {

    @Getter
    private final TeamKey key;
    private final Translations translations;

    public TeamRef(TeamKey key, Translations translations) {
        this.key = key;
        this.translations = translations;
    }

    @Override
    public Text getNameFor(ServerPlayerEntity viewer) {
        return translations.translateText(viewer, key.getTranslationKey())
                .styled(style -> style.withColor(key.color()));
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
