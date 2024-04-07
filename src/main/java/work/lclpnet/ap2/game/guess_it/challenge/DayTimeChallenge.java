package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.MinecraftDayTime;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.OptionalInt;
import java.util.Random;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DayTimeChallenge implements Challenge, SchedulerAction {

    private static final int
            ANIMATION_DURATION_TICKS = Ticks.seconds(2),
            DURATION_TICKS = Ticks.seconds(15);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private int timeStart = 6000, timeEnd = 6000;
    private int correctTime = 6000, prevTime = 6000;
    private int tick = 0;
    private TaskHandle animation = null;

    public DayTimeChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_ESTIMATE;
    }

    @Override
    public int getDurationTicks() {
        return Math.max(ANIMATION_DURATION_TICKS, DURATION_TICKS);
    }

    @Override
    public void prepare() {
        prevTime = (int) world.getTimeOfDay();
        correctTime = random.nextInt(24000);

        animateTime(prevTime, correctTime);
    }

    @Override
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validate((str, player) -> MinecraftDayTime.dayTimeValue(str),
                str -> translations.translateText("game.ap2.guess_it.input.daytime", styled(str, YELLOW)).formatted(RED));

        translations.translateText("game.ap2.guess_it.daytime.guess")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));

        var packet = new PlayerSpawnPositionS2CPacket(BlockPos.ORIGIN.north(10000), 0);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            // give compass and make it point north
            player.networkHandler.sendPacket(packet);
            player.getInventory().setStack(4, new ItemStack(Items.COMPASS));
        }
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(MinecraftDayTime.stringifyDayTime(correctTime));

        Participants participants = gameHandle.getParticipants();

        result.grantClosest3Diff(participants.getAsSet(), player -> {
            var optChoice = choices.get(player);
            if (optChoice.isEmpty()) return OptionalInt.empty();

            var optTime = MinecraftDayTime.parseDayTime(optChoice.get());
            if (optTime.isEmpty()) return OptionalInt.empty();

            int time = optTime.getAsInt();

            // wrap time at 0 am
            int diff1 = Math.floorMod(correctTime - time, 24000);
            int diff2 = Math.floorMod(time - correctTime, 24000);

            return OptionalInt.of(Math.min(diff1, diff2));
        });

        animateTime(correctTime, prevTime);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.getInventory().setStack(4, ItemStack.EMPTY);
        }
    }

    @Override
    public void destroy() {
        world.setTimeOfDay(prevTime);

        if (animation != null) {
            animation.cancel();
            animation = null;
        }
    }

    private void animateTime(int start, int end) {
        tick = 0;
        timeStart = start;
        timeEnd = end;

        animation = gameHandle.getGameScheduler().interval(this, 1);
    }

    @Override
    public void run(RunningTask info) {
        float t = tick++;
        float progress = t / ANIMATION_DURATION_TICKS;

        int time = getInterpolatedTime(progress);

        world.setTimeOfDay(time);

        var packet = new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE));

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.networkHandler.sendPacket(packet);
        }

        if (t >= ANIMATION_DURATION_TICKS) {
            info.cancel();
        }
    }

    private int getInterpolatedTime(float progress) {
        int forwardDiff = Math.floorMod(timeEnd - timeStart, 24000);
        int backwardDiff = Math.floorMod(timeStart - timeEnd, 24000);

        int time;

        if (timeStart > timeEnd && forwardDiff <= backwardDiff) {
            // advance time forwards, wrapping at 0 am
            time = Math.floorMod(MathHelper.lerp(progress, timeStart, timeEnd + 24000), 24000);
        } else if (timeStart < timeEnd && forwardDiff >= backwardDiff) {
            // advance time backwards, wrapping at 0 am
            time = Math.floorMod(MathHelper.lerp(progress, timeStart + 24000, timeEnd), 24000);
        } else {
            // advance time without wrapping
            time = MathHelper.lerp(progress, timeStart, timeEnd);
        }

        return time;
    }
}
