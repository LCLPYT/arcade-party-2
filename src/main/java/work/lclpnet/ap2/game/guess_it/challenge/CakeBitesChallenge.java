package work.lclpnet.ap2.game.guess_it.challenge;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Random;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.DARK_GREEN;

public class CakeBitesChallenge implements Challenge {

    private static final int DURATION_TICKS = Ticks.seconds(16);
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Random random;
    private final Stage stage;
    private final WorldModifier modifier;
    private int amount = 0;

    public CakeBitesChallenge(MiniGameHandle gameHandle, ServerWorld world, Random random, Stage stage, WorldModifier modifier) {
        this.gameHandle = gameHandle;
        this.world = world;
        this.random = random;
        this.stage = stage;
        this.modifier = modifier;
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
    public void begin(InputInterface input) {
        TranslationService translations = gameHandle.getTranslations();

        input.expectInput().validateInt(translations);

        amount = random.nextInt(7);

        createCake();

        translations.translateText("game.ap2.guess_it.cake_bites")
                .formatted(DARK_GREEN, BOLD)
                .sendTo(PlayerLookup.world(world));
    }

    private void createCake() {
        var cake = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        DisplayEntityAccess.setBlockState(cake, Blocks.CAKE.getDefaultState().with(CakeBlock.BITES, amount));

        float scale = 7;

        AffineTransformation transformation = new AffineTransformation(new Matrix4f(
                scale, 0, 0, 0,
                0, scale, 0, 0,
                0, 0, scale, 0,
                0, 0, 0, 1
        ));

        DisplayEntityAccess.setTransformation(cake, transformation);

        BlockPos origin = stage.getOrigin();
        double x = origin.getX() + 0.5 - scale * 0.5;
        double y = origin.getY();
        double z = origin.getZ() + 0.5 - scale * 0.5;

        cake.setPos(x, y, z);

        modifier.spawnEntity(cake);
    }

    @Override
    public void evaluate(PlayerChoices choices, ChallengeResult result) {
        result.setCorrectAnswer(amount);
        result.grantClosest3(gameHandle.getParticipants().getAsSet(), amount, choices::getInt);
    }
}
