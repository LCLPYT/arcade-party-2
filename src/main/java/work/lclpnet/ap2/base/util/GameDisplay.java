package work.lclpnet.ap2.base.util;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntity;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.ap2.impl.util.world.entity.TranslatedTextDisplay;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.impl.util.TextUtil.*;

public class GameDisplay {

    private final ScreenConfig config;
    private final ServerWorld world;
    private final HookRegistrar hooks;
    private final Translations translations;
    private final DynamicEntityManager dynamicEntityManager;
    private final List<DynamicEntity> dynamicEntities = new ArrayList<>();

    public GameDisplay(ScreenConfig config, ServerWorld world, HookRegistrar hooks, Translations translations,
                       DynamicEntityManager dynamicEntityManager) {
        this.config = config;
        this.world = world;
        this.hooks = hooks;
        this.translations = translations;
        this.dynamicEntityManager = dynamicEntityManager;
    }

    public synchronized void displayDefault() {
        clear();

        var title = new TranslatedTextDisplay(world, translations);

        title.setText(translations.translateText("game.%s.title".formatted(ApConstants.ID)).formatted(GRAY, ITALIC));
        title.setLineWidth(100);

        float scale = 2;
        title.setPosition(new Vec3d(config.center().sub(0, DISPLAY_LINE_HEIGHT * scale * 0.5F, 0)));

        title.setTransformation(new AffineTransformation(new Matrix4f()
                .scale(scale)
                .rotateAffine(MathUtil.rotation(new Vector3f(0, 0, 1), config.normal()))));

        spawnDynamic(title);
    }

    public synchronized void displayGame(MiniGame game) {
        clear();

        Quaternionf textRotation = config.textRotation();

        // title
        var title = new TranslatedTextDisplay(world, translations);

        title.setText(translations.translateText(game.getTitleKey()).formatted(AQUA, BOLD));
        title.setLineWidth(200);

        float titleScale = 2;

        title.setPosition(new Vec3d(config.right().mul(config.width() * 0.5f)
                .add(config.pos())
                .add(config.up().mul(config.height() - (FONT_HEIGHT + 5) * DISPLAY_FONT_SCALE * titleScale))));

        title.setTransformation(new AffineTransformation(new Matrix4f()
                .scale(titleScale)
                .rotateAffine(textRotation)));

        spawnDynamic(title);

        // description
        var description = new TranslatedTextDisplay(world, translations);

        description.setText(translations.translateText(game.getDescriptionKey(), game.getDescriptionArguments())
                .formatted(GREEN));
        description.setLineWidth(150);
        description.setTextAlignment(DisplayEntity.TextDisplayEntity.TextAlignment.LEFT);

        float descriptionScale = 1;

        description.setPosition(new Vec3d(config.right().mul(config.width() * 0.33f)
                .add(config.pos())
                .add(config.up().mul(config.height() * 0.35f))));

        description.setTransformation(new AffineTransformation(new Matrix4f()
                .scale(descriptionScale)
                .rotateAffine(textRotation)));

        spawnDynamic(description);
    }

    private void spawnDynamic(TranslatedTextDisplay title) {
        dynamicEntities.add(title);
        dynamicEntityManager.add(title);
    }

    private void clear() {
        for (DynamicEntity dynamic : dynamicEntities) {
            dynamicEntityManager.remove(dynamic);
        }

        dynamicEntities.clear();
    }
}
