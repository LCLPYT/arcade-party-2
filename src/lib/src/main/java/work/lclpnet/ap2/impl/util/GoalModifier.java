package work.lclpnet.ap2.impl.util;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.Set;

public class GoalModifier {

    public static void clear(GoalSelector selector) {
        clearGoals(selector.getAvailableGoals());
    }

    public static void clearGoals(Set<WrappedGoal> goals) {
        for (WrappedGoal goal : goals) {
            if (goal.isRunning()) {
                goal.stop();
            }
        }

        goals.clear();
    }
}
