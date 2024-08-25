package work.lclpnet.ap2.base.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntity;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.ap2.impl.util.world.entity.TranslatedTextDisplay;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.Formatting.GRAY;
import static net.minecraft.util.Formatting.ITALIC;

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
        Vec3d position = new Vec3d(config.center().sub(0, TextUtil.DISPLAY_LINE_HEIGHT * scale * 0.5F, 0));

        title.setPosition(position);

        title.setTransformation(new AffineTransformation(new Matrix4f()
                .scale(scale)
                .rotateAffine(MathUtil.rotation(new Vector3f(0, 0, 1), config.normal()))));

        spawnDynamic(title);
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
