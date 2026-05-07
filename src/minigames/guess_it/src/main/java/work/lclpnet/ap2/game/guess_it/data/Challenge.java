package work.lclpnet.ap2.game.guess_it.data;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
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

    default void provideInitCommand(LiteralArgumentBuilder<CommandSourceStack> node, Initializer init) {}

    default void addHint(DynamicEntityModifier dynamicEntities, ServerLevel world, Translations translations, Vec3 pos, String key) {
        var label = new TranslatedTextDisplay(world, translations);

        var controller = label.controller();
        controller.setBillboardMode(Display.BillboardConstraints.CENTER);
        controller.setTransformation(new Transformation(new Matrix4f().scale(3)));
        controller.setPosition(pos);
        controller.setText(translations.translateText(key).formatted(ChatFormatting.GREEN));
        controller.setBrightness(new Brightness(15, 15));

        dynamicEntities.spawn(label);
    }

    interface Initializer {
        void accept(CommandContext<CommandSourceStack> ctx, Object config);
    }
}
