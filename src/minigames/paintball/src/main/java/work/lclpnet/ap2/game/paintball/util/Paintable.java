package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.world.level.block.Block;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;

public interface Paintable {

    Block blockFor(DyeTeamKey team);
}
