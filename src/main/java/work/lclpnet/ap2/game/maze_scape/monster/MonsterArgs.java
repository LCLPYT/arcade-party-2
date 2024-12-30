package work.lclpnet.ap2.game.maze_scape.monster;

import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;

import java.util.UUID;

public record MonsterArgs(UUID uuid, MSManager manager, Logger logger) {
}
