package work.lclpnet.ap2.game.paintball.paint;

import net.minecraft.block.Block;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;

public interface Paintable {

    Block blockFor(DyeTeamKey team);
}
