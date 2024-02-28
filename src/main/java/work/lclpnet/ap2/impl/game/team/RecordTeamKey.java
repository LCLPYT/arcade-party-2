package work.lclpnet.ap2.impl.game.team;

import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.game.team.TeamKey;

public record RecordTeamKey(String id, Formatting colorFormat) implements TeamKey {

}
