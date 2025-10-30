package work.lclpnet.ap2.game.guess_it.data;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier;
import work.lclpnet.gaco.dynamic_entities.TranslatedTextDisplay;
import work.lclpnet.kibu.translate.Translations;

public interface Challenge {

    String id();

    String getPreparationKey();

    int getDurationTicks();

    void begin(InputInterface input, ChallengeMessenger messenger);

    void evaluate(PlayerChoices choices, ChallengeResult result);

    default void destroy() {}

    default void prepare() {}

    default boolean shouldPlayBeginSound() {
        return true;
    }

    default void init(@Nullable Object init) {}

    default void provideInitCommand(LiteralArgumentBuilder<ServerCommandSource> node, Initializer init) {}

    default void addHint(DynamicEntityModifier dynamicEntities, ServerWorld world, Translations translations, Vec3d pos, String key) {
        var label = new TranslatedTextDisplay(world, translations);

        var controller = label.controller();
        controller.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        controller.setTransformation(new AffineTransformation(new Matrix4f().scale(3)));
        controller.setPosition(pos);
        controller.setText(translations.translateText(key).formatted(Formatting.GREEN));
        controller.setBrightness(new Brightness(15, 15));

        dynamicEntities.spawn(label);
    }

    interface Initializer {
        void accept(CommandContext<ServerCommandSource> ctx, Object config);
    }
}
