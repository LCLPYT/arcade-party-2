package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.challenge.MobCountSingleChallenge;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.*;

public class GuessItManager {

    private final Random random;
    private final Set<Challenge> challenges = new HashSet<>();
    private final List<Challenge> queue = new ArrayList<>();

    public GuessItManager(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage,
                          WorldModifier modifier) {
        this.random = random;

//        challenges.add(new MathsChallenge(gameHandle, world, random));
//        challenges.add(new DayTimeChallenge(gameHandle, world, random));
        challenges.add(new MobCountSingleChallenge(gameHandle, world, random, stage, modifier));
    }

    @NotNull
    public Challenge nextChallenge() {
        if (queue.isEmpty()) {
            if (challenges.isEmpty()) {
                throw new IllegalStateException("No challenges registered");
            }

            queue.addAll(challenges);
            Collections.shuffle(queue, random);
        }

        return queue.remove(0);
    }
}
