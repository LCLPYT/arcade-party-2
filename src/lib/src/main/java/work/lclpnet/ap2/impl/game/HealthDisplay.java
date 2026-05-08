package work.lclpnet.ap2.impl.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;

import static java.lang.Math.ceil;
import static java.lang.Math.clamp;

public class HealthDisplay {

    private final MiniGameHandle gameHandle;

    public HealthDisplay(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    public void setup(HookRegistrar hooks) {
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();

        Objective objective = manager.createObjective("health_name", ObjectiveCriteria.DUMMY, Component.empty(), ObjectiveCriteria.RenderType.HEARTS);
        objective.setDisplayAutoUpdate(false);

        manager.setDisplay(DisplaySlot.BELOW_NAME, objective);
        manager.setDisplay(DisplaySlot.LIST, objective);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            float health = player.getHealth();
            update(player, health, objective);
        }

        EntityHealthCallback.HOOK.registerWith(hooks, (entity, health) -> {
            float oldHealth = entity.getHealth();

            if (entity instanceof ServerPlayer player && gameHandle.getParticipants().isParticipating(player) && health < oldHealth) {
                // update the scoreboard
                update(player, health, objective);
            }

            return false;
        });

        PlayerEliminatedCallback.HOOK.registerWith(hooks, player -> {
            manager.setScore(player, objective, 0);
            manager.setNumberFormat(player, objective, BlankFormat.INSTANCE);
        });
    }

    private void update(ServerPlayer player, float health, Objective objective) {
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();

        manager.setScore(player, objective, (int) ceil(health));
        manager.setNumberFormat(player, objective, new FixedFormat(healthText(health)));
    }

    private Component healthText(float health) {
        int hearts = clamp((int) ceil(health), 0, 20);
        boolean half = hearts % 2 == 1;
        hearts >>= 1;

        MutableComponent text = Component.literal(" " + "♥".repeat(hearts)).withColor(0xff1313);

        if (half) {
            text.append(Component.literal("♡").withColor(0xff1313));
            hearts += 1;
        }

        if (hearts < 10) {
            text.append(Component.literal("♡".repeat(10 - hearts)).withColor(0x282828));
        }

        return text;
    }
}
