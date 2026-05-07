package work.lclpnet.ap2.game.guess_it.challenge;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Random;

public class CakeBitesChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(14);
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Random random;
    private final BlockShape blockShape;
    private final WorldModifier modifier;
    private final DynamicEntityModifier dynamicEntities;
    private int amount = 0;

    public CakeBitesChallenge(MiniGameHandle gameHandle, ServerLevel world, Random random, BlockShape blockShape, WorldModifier modifier, DynamicEntityModifier dynamicEntities) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.blockShape = blockShape;
        this.modifier = modifier;
        this.dynamicEntities = dynamicEntities;
    }

    @Override
    public String id() {
        return "cake_bites";
    }

    @Override
    public String getPreparationKey() {
        return GuessItConstants.PREPARE_ESTIMATE;
    }

    @Override
    public int getDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(InputInterface input, ChallengeMessenger messenger) {
        Translations translations = gameHandle.getTranslations();
        messenger.task(translations.translateText("game.ap2.guess_it.cake_bites"));

        input.expectInput().validateInt(translations);

        amount = random.nextInt(7);

        createCake();

        BlockPos origin = blockShape.origin();

        addHint(dynamicEntities, world, gameHandle.getTranslations(),
                new Vec3(origin.getX() + 0.5, origin.getY() + 4.5, origin.getZ() + 0.5),
                "game.ap2.guess_it.cake_bites.hint");
    }

    private void createCake() {
        var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world);
        DisplayEntityAccess.setBlockState(display, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, amount));

        float scale = 7;

        DisplayEntityAccess.setTransformation(display, new Transformation(new Matrix4f().scale(7)));

        BlockPos origin = blockShape.origin();
        double x = origin.getX() + 0.5 - scale * 0.5;
        double y = origin.getY();
        double z = origin.getZ() + 0.5 - scale * 0.5;

        display.setPosRaw(x, y, z);

        modifier.spawnEntity(display);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }
}
