package work.lclpnet.ap2.impl.game.team;

import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.game.team.TeamKey;

public class ApTeamKeys {

    public static final TeamKey
            RED = new RecordTeamKey("red", Formatting.RED),
            BLUE = new RecordTeamKey("blue", Formatting.BLUE);
}
