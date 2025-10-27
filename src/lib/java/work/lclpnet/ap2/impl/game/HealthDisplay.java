package work.lclpnet.ap2.impl.game;

import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;

import static java.lang.Math.*;

public class HealthDisplay {

    private final MiniGameHandle gameHandle;

    public HealthDisplay(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    public void setup(HookRegistrar hooks) {
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();

        ScoreboardObjective objective = manager.createObjective("health_name", ScoreboardCriterion.DUMMY, Text.empty(), ScoreboardCriterion.RenderType.HEARTS);
        objective.setDisplayAutoUpdate(false);

        manager.setDisplay(ScoreboardDisplaySlot.BELOW_NAME, objective);
        manager.setDisplay(ScoreboardDisplaySlot.LIST, objective);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            float health = player.getHealth();
            update(player, health, objective);
        }

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            float oldHealth = entity.getHealth();

            if (entity instanceof ServerPlayerEntity player && gameHandle.getParticipants().isParticipating(player) && health < oldHealth) {
                // update the scoreboard
                update(player, health, objective);
            }

            return false;
        });

        hooks.registerHook(PlayerEliminatedCallback.HOOK, player -> {
            manager.setScore(player, objective, 0);
            manager.setNumberFormat(player, objective, BlankNumberFormat.INSTANCE);
        });
    }

    private void update(ServerPlayerEntity player, float health, ScoreboardObjective objective) {
        CustomScoreboardManager manager = gameHandle.getScoreboardManager();

        manager.setScore(player, objective, (int) ceil(health));
        manager.setNumberFormat(player, objective, new FixedNumberFormat(healthText(health)));
    }

    private Text healthText(float health) {
        int hearts = max(0, min(20, (int) ceil(health)));
        boolean half = hearts % 2 == 1;
        hearts >>= 1;

        MutableText text = Text.literal(" " + "♥".repeat(hearts)).withColor(0xff1313);

        if (half) {
            text.append(Text.literal("♡").withColor(0xff1313));
            hearts += 1;
        }

        if (hearts < 10) {
            text.append(Text.literal("♡".repeat(10 - hearts)).withColor(0x282828));
        }

        return text;
    }
}
