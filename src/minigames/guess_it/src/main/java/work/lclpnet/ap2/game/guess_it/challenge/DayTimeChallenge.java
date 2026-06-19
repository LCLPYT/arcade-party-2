package work.lclpnet.ap2.game.guess_it.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.ext.mc.LevelExtensionsKt;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier;
import work.lclpnet.ap2.game.guess_it.util.MinecraftDayTime;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.translate.Translations;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import static net.minecraft.ChatFormatting.RED;
import static net.minecraft.ChatFormatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DayTimeChallenge implements Challenge, SchedulerAction {

    private static final int
            ANIMATION_DURATION_TICKS = Ticks.seconds(2),
            DURATION_TICKS = Ticks.seconds(20);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final DynamicEntityModifier dynamicEntities;
    private int timeStart = 6000, timeEnd = 6000;
    private int correctTime = 6000, prevTime = 6000;
    private int tick = 0;
    private TaskHandle animation = null;

    public DayTimeChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random,
                            BlockShape blockShape, DynamicEntityModifier dynamicEntities) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.dynamicEntities = dynamicEntities;
    }

    @Override
    public String id() {
        return "day_time";
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
        prevTime = (int) world.getOverworldClockTime();
        correctTime = random.nextInt(24000);

        animateTime(prevTime, correctTime);
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("daytime.guess"));

        input.expectInput().validate((str, _) -> MinecraftDayTime.dayTimeValue(str),
                str -> translations.translateText("input.daytime", styled(str, YELLOW)).withStyle(RED));

        // create compass that points north
        ItemStack stack = new ItemStack(Items.COMPASS);

        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(new GlobalPos(world.dimension(), BlockPos.ZERO.north(10000))),
                false));

        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(Items.COMPASS)
                .withStyle(style -> style.withItalic(false)));

        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            player.getInventory().setItem(4, stack.copy());
        }

        BlockPos origin = blockShape.origin();

        addHint(dynamicEntities, world, gameHandle.getTranslations(),
                new Vec3(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5),
                "daytime.hint");
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

        for (ServerPlayer player : gameHandle.getParticipants()) {
            player.getInventory().setItem(4, ItemStack.EMPTY);
        }
    }

    @Override
    public void destroy() {
        LevelExtensionsKt.setDayTime(world, prevTime);

        if (animation != null) {
            animation.cancel();
            animation = null;
        }
    }

    private void animateTime(int start, int end) {
        tick = 0;
        timeStart = start;
        timeEnd = end;

        animation = gameHandle.getScheduler().interval(this, 1);
    }

    @Override
    public void run(RunningTask info) {
        float t = tick++;
        float progress = t / ANIMATION_DURATION_TICKS;

        int time = getInterpolatedTime(progress);

        LevelExtensionsKt.setDayTime(world, time);

        // TODO still needed?
//        var packet = new ClientboundSetTimePacket(world.getGameTime(), world.getOverworldClockTime(), world.getGameRules().get(GameRules.ADVANCE_TIME));
//
//        for (ServerPlayer player : PlayerLookup.level(world)) {
//            player.connection.send(packet);
//        }

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
            time = Math.floorMod(Mth.lerpInt(progress, timeStart, timeEnd + 24000), 24000);
        } else if (timeStart < timeEnd && forwardDiff >= backwardDiff) {
            // advance time backwards, wrapping at 0 am
            time = Math.floorMod(Mth.lerpInt(progress, timeStart + 24000, timeEnd), 24000);
        } else {
            // advance time without wrapping
            time = Mth.lerpInt(progress, timeStart, timeEnd);
        }

        return time;
    }
}
