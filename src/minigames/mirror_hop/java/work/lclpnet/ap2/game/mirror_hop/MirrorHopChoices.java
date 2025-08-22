package work.lclpnet.ap2.game.mirror_hop;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;

public class MirrorHopChoices {

    private final List<Choice> choices;
    private final int[] correct;

    public MirrorHopChoices(List<Choice> choices) {
        this.choices = choices;
        this.correct = new int[choices.size()];
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void randomize(Random random) {
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);

            correct[i] = random.nextInt(choice.platforms().size());
        }
    }

    public void addColliders(CollisionDetector collisionDetector) {
        for (Choice choice : choices) {
            var platforms = choice.platforms();

            for (Platform platform : platforms) {
                collisionDetector.add(platform);
            }
        }
    }

    public int getChoiceIndex(Platform platform) {
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);

            if (choice.platforms.contains(platform)) {
                return i;
            }
        }

        return -1;
    }

    public boolean isCorrect(Platform platform, int choiceIndex) {
        if (choiceIndex < 0) {
            throw new IllegalArgumentException("Choice index must not be negative");
        }

        Choice choice = choices.get(choiceIndex);

        int platformIndex = choice.platforms.indexOf(platform);

        if (platformIndex == -1) {
            throw new IllegalArgumentException("Platform does not belong to the given choice");
        }

        return correct[choiceIndex] == platformIndex;
    }

    public static MirrorHopChoices from(GameMap map, Logger logger) {
        JSONArray array = map.requireProperty("choices");

        return from(array, logger);
    }

    public static MirrorHopChoices from(JSONArray json, Logger logger) {
        var choices = ImmutableList.<Choice>builder();

        for (Object obj : json) {
            if (!(obj instanceof JSONArray arr)) {
                logger.warn("Invalid choice item of type {}", obj.getClass().getSimpleName());
                continue;
            }

            if (arr.length() < 2) {
                logger.warn("There should be at least 2 platform for each choice");
            }

            var platforms = ImmutableList.<Platform>builder();

            for (Object pObj : arr) {
                if (!(pObj instanceof JSONArray pArr)) {
                    logger.warn("Invalid platform item of type {}", pObj.getClass().getSimpleName());
                    continue;
                }

                BlockBox ground = MapUtil.readBox(pArr);

                platforms.add(new Platform(ground));
            }

            var immutablePlatforms = platforms.build();

            if (immutablePlatforms.isEmpty()) {
                logger.warn("No platforms, skipping entry");
                continue;
            }

            choices.add(new Choice(immutablePlatforms));
        }

        return new MirrorHopChoices(choices.build());
    }

    public record Choice(List<Platform> platforms) {}

    public static class Platform implements Collider {

        private final BlockBox ground;
        private final int minBx, minBy, minBz;
        private final int maxBx, maxBy, maxBz;

        public Platform(BlockBox ground) {
            this.ground = ground;

            BlockPos min = ground.min();
            this.minBx = min.getX();
            this.minBy = min.getY();
            this.minBz = min.getZ();

            BlockPos max = ground.max();
            this.maxBx = max.getX() + 1;
            this.maxBy = max.getY() + 3;
            this.maxBz = max.getZ() + 1;
        }

        @Override
        public boolean collidesWith(double x, double y, double z) {
            return x >= minBx && x < maxBx
                   && y >= minBy && y < maxBy
                   && z >= minBz && z < maxBz;
        }

        @Override
        public BlockPos min() {
            return ground.min();  // height is not important, therefore ground can be used
        }

        @Override
        public BlockPos max() {
            return ground.max();  // height is not important, therefore ground can be used
        }

        public BlockBox getGround() {
            return ground;
        }
    }
}
