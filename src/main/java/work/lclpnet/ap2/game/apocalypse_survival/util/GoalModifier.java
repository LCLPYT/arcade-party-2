package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;

import java.util.Set;

public class GoalModifier {

    public static void clear(GoalSelector selector) {
        clearGoals(selector.getGoals());
    }

    public static void clearGoals(Set<PrioritizedGoal> goals) {
        for (PrioritizedGoal goal : goals) {
            if (goal.isRunning()) {
                goal.stop();
            }
        }

        goals.clear();
    }
}
