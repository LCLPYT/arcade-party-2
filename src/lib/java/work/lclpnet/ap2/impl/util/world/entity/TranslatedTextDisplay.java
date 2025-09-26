package work.lclpnet.ap2.impl.util.world.entity;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.ds.RefCounted;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;

public class TranslatedTextDisplay implements DynamicEntity {

    private final Translations translations;
    private final ControllerImpl controller;

    public TranslatedTextDisplay(ServerWorld world, Translations translations) {
        this.translations = translations;
        controller = new ControllerImpl(world);
    }

    @Override
    public Vec3d getPosition() {
        return controller.getPosition();
    }

    @Override
    public Entity getEntity(ServerPlayerEntity player) {
        return controller.ref(translations.getLanguage(player), display -> {});
    }

    @Override
    public void cleanup(ServerPlayerEntity player) {
        controller.deref(translations.getLanguage(player));
    }

    public Controller controller() {
        return controller;
    }

    public interface Controller {
        void setText(TextTranslatable text);
        TextTranslatable getText();
        void setPosition(Vec3d position);
        Vec3d getPosition();
        void setLineWidth(int lineWidth);
        int getLineWidth();
        void setTextOpacity(byte textOpacity);
        byte getTextOpacity();
        void setBackground(int background);
        int getBackground();
        void setDisplayFlags(byte displayFlags);
        byte getDisplayFlags();
        void setTransformation(AffineTransformation transformation);
        AffineTransformation getTransformation();
        void setInterpolationDuration(int interpolationDuration);
        int getInterpolationDuration();
        void setTeleportDuration(int teleportDuration);
        int getTeleportDuration();
        void setStartInterpolation(int startInterpolation);
        int getStartInterpolation();
        void setBillboardMode(DisplayEntity.BillboardMode billboardMode);
        DisplayEntity.BillboardMode getBillboardMode();
        void setBrightness(@Nullable Brightness brightness);
        @Nullable Brightness getBrightness();
        void setViewRange(float viewRange);
        float getViewRange();
        void setShadowRadius(float shadowRadius);
        float getShadowRadius();
        void setShadowStrength(float shadowStrength);
        float getShadowStrength();
        void setDisplayWidth(float displayWidth);
        float getDisplayWidth();
        void setDisplayHeight(float displayHeight);
        float getDisplayHeight();
        void setGlowColorOverride(int glowColorOverride);
        int getGlowColorOverride();

        default void configure(Consumer<Controller> action) {
            action.accept(this);
        }
    }

    public static class ControllerImpl implements Controller {

        @Setter private @Nullable ServerWorld world;
        @Getter private final RefCounted<String, DisplayEntity.TextDisplayEntity> entities = new RefCounted<>(HashMap::new);
        @Getter private TextTranslatable text = TranslatedText.create(lang -> RootText.create(), player -> "");  // empty by default
        @Getter private Vec3d position = Vec3d.ZERO;
        @Getter private int lineWidth = 200;
        @Getter private byte textOpacity = (byte) -1;
        @Getter private int background = 0;
        @Getter private byte displayFlags = (byte) 0;
        @Getter private AffineTransformation transformation = AffineTransformation.identity();
        @Getter private int interpolationDuration = 0;
        @Getter private int teleportDuration = 0;
        @Getter private int startInterpolation = 0;
        @Getter private DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;
        private @Nullable Brightness brightness = null;
        @Getter private float viewRange = 1.0F;
        @Getter private float shadowRadius = 0.0F;
        @Getter private float shadowStrength = 1.0F;
        @Getter private float displayWidth = 0.0F;
        @Getter private float displayHeight = 0.0F;
        @Getter private int glowColorOverride = -1;

        public ControllerImpl(@Nullable ServerWorld world) {
            this.world = world;
        }

        public DisplayEntity.TextDisplayEntity ref(String language, Consumer<DisplayEntity.TextDisplayEntity> init) {
            return entities.reference(language, lang -> {
                var textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
                textDisplay.setPosition(position);

                textDisplay.setText(text.translateTo(lang));
                textDisplay.setLineWidth(lineWidth);
                textDisplay.setTextOpacity(textOpacity);
                textDisplay.setBackground(background);
                textDisplay.setDisplayFlags(displayFlags);
                textDisplay.setTransformation(transformation);
                textDisplay.setInterpolationDuration(interpolationDuration);
                textDisplay.setTeleportDuration(teleportDuration);
                textDisplay.setStartInterpolation(startInterpolation);
                textDisplay.setBillboardMode(billboardMode);
                textDisplay.setBrightness(brightness);
                textDisplay.setViewRange(viewRange);
                textDisplay.setShadowRadius(shadowRadius);
                textDisplay.setShadowStrength(shadowStrength);
                textDisplay.setDisplayWidth(displayWidth);
                textDisplay.setDisplayHeight(displayHeight);
                textDisplay.setGlowColorOverride(glowColorOverride);

                init.accept(textDisplay);

                return textDisplay;
            });
        }

        public void deref(String language) {
            entities.dereference(language);
        }

        @Override
        public void setText(TextTranslatable text) {
            this.text = text;

            entities.forEach((lang, display) -> display.setText(text.translateTo(lang)));
        }

        @Override
        public void setPosition(Vec3d position) {
            this.position = Objects.requireNonNull(position);

            entities.forEach(display -> display.setPosition(position));
        }

        @Override
        public void setLineWidth(int lineWidth) {
            this.lineWidth = lineWidth;

            entities.forEach(display -> display.setLineWidth(lineWidth));
        }

        @Override
        public void setTextOpacity(byte textOpacity) {
            this.textOpacity = textOpacity;

            entities.forEach(display -> display.setTextOpacity(textOpacity));
        }

        @Override
        public void setBackground(int background) {
            this.background = background;

            entities.forEach(display -> display.setBackground(background));
        }

        @Override
        public void setDisplayFlags(byte displayFlags) {
            this.displayFlags = displayFlags;

            entities.forEach(display -> display.setDisplayFlags(displayFlags));
        }

        @Override
        public void setTransformation(AffineTransformation transformation) {
            this.transformation = transformation;

            entities.forEach(display -> display.setTransformation(transformation));
        }

        @Override
        public void setInterpolationDuration(int interpolationDuration) {
            this.interpolationDuration = interpolationDuration;

            entities.forEach(display -> display.setInterpolationDuration(interpolationDuration));
        }

        @Override
        public void setTeleportDuration(int teleportDuration) {
            this.teleportDuration = teleportDuration;

            entities.forEach(display -> display.setTeleportDuration(teleportDuration));
        }

        @Override
        public void setStartInterpolation(int startInterpolation) {
            this.startInterpolation = startInterpolation;

            entities.forEach(display -> display.setStartInterpolation(startInterpolation));
        }

        @Override
        public void setBillboardMode(DisplayEntity.BillboardMode billboardMode) {
            this.billboardMode = billboardMode;

            entities.forEach(display -> display.setBillboardMode(billboardMode));
        }

        @Override
        public void setBrightness(@Nullable Brightness brightness) {
            this.brightness = brightness;

            entities.forEach(display -> display.setBrightness(brightness));
        }

        @Override
        public @Nullable Brightness getBrightness() {
            return brightness;
        }

        @Override
        public void setViewRange(float viewRange) {
            this.viewRange = viewRange;

            entities.forEach(display -> display.setViewRange(viewRange));
        }

        @Override
        public void setShadowRadius(float shadowRadius) {
            this.shadowRadius = shadowRadius;

            entities.forEach(display -> display.setShadowRadius(shadowRadius));
        }

        @Override
        public void setShadowStrength(float shadowStrength) {
            this.shadowStrength = shadowStrength;

            entities.forEach(display -> display.setShadowStrength(shadowStrength));
        }

        @Override
        public void setDisplayWidth(float displayWidth) {
            this.displayWidth = displayWidth;

            entities.forEach(display -> display.setDisplayWidth(displayWidth));
        }

        @Override
        public void setDisplayHeight(float displayHeight) {
            this.displayHeight = displayHeight;

            entities.forEach(display -> display.setDisplayHeight(displayHeight));
        }

        @Override
        public void setGlowColorOverride(int glowColorOverride) {
            this.glowColorOverride = glowColorOverride;

            entities.forEach(display -> display.setGlowColorOverride(glowColorOverride));
        }
    }
}
