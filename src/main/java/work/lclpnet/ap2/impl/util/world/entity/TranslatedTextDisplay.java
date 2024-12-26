package work.lclpnet.ap2.impl.util.world.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.RefCounted;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashMap;
import java.util.Objects;

public class TranslatedTextDisplay implements DynamicEntity {

    private final ServerWorld world;
    private final Translations translations;
    private final RefCounted<String, DisplayEntity.TextDisplayEntity> entities = new RefCounted<>(HashMap::new);
    private Vec3d position = Vec3d.ZERO;
    private TranslatedText text = TranslatedText.create(lang -> RootText.create(), player -> "");  // empty by default
    private int lineWidth = 200;
    private byte textOpacity = (byte) -1;
    private int background = 0;
    private byte displayFlags = (byte) 0;
    private AffineTransformation transformation = AffineTransformation.identity();
    private int interpolationDuration = 0;
    private int startInterpolation = 0;
    private DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;
    private @Nullable Brightness brightness = null;
    private float viewRange = 1.0F;
    private float shadowRadius = 0.0F;
    private float shadowStrength = 1.0F;
    private float displayWidth = 0.0F;
    private float displayHeight = 0.0F;
    private int glowColorOverride = -1;

    public TranslatedTextDisplay(ServerWorld world, Translations translations) {
        this.world = world;
        this.translations = translations;
    }

    public void setPosition(Vec3d position) {
        this.position = Objects.requireNonNull(position);

        entities.forEach(display -> display.setPosition(position));
    }

    @Override
    public Vec3d getPosition() {
        return position;
    }

    public void setText(TranslatedText text) {
        this.text = text;

        entities.forEach((lang, display) -> DisplayEntityAccess.setText(display, text.translateTo(lang)));
    }

    public TranslatedText getText() {
        return text;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;

        entities.forEach(display -> DisplayEntityAccess.setLineWidth(display, lineWidth));
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setTextOpacity(byte textOpacity) {
        this.textOpacity = textOpacity;

        entities.forEach(display -> DisplayEntityAccess.setTextOpacity(display, textOpacity));
    }

    public byte getTextOpacity() {
        return textOpacity;
    }

    public void setBackground(int background) {
        this.background = background;

        entities.forEach(display -> DisplayEntityAccess.setBackground(display, background));
    }

    public int getBackground() {
        return background;
    }

    public void setDisplayFlags(byte displayFlags) {
        this.displayFlags = displayFlags;

        entities.forEach(display -> DisplayEntityAccess.setDisplayFlags(display, displayFlags));
    }

    public byte getDisplayFlags() {
        return displayFlags;
    }

    public void setTransformation(AffineTransformation transformation) {
        this.transformation = transformation;

        entities.forEach(display -> DisplayEntityAccess.setTransformation(display, transformation));
    }

    public AffineTransformation getTransformation() {
        return transformation;
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;

        entities.forEach(display -> DisplayEntityAccess.setInterpolationDuration(display, interpolationDuration));
    }

    public int getInterpolationDuration() {
        return interpolationDuration;
    }

    public void setStartInterpolation(int startInterpolation) {
        this.startInterpolation = startInterpolation;

        entities.forEach(display -> DisplayEntityAccess.setStartInterpolation(display, startInterpolation));
    }

    public int getStartInterpolation() {
        return startInterpolation;
    }

    public void setBillboardMode(DisplayEntity.BillboardMode billboardMode) {
        this.billboardMode = billboardMode;

        entities.forEach(display -> DisplayEntityAccess.setBillboardMode(display, billboardMode));
    }

    public DisplayEntity.BillboardMode getBillboardMode() {
        return billboardMode;
    }

    public void setBrightness(@Nullable Brightness brightness) {
        this.brightness = brightness;

        entities.forEach(display -> DisplayEntityAccess.setBrightness(display, brightness));
    }

    public @Nullable Brightness getBrightness() {
        return brightness;
    }

    public void setViewRange(float viewRange) {
        this.viewRange = viewRange;

        entities.forEach(display -> DisplayEntityAccess.setViewRange(display, viewRange));
    }

    public float getViewRange() {
        return viewRange;
    }

    public void setShadowRadius(float shadowRadius) {
        this.shadowRadius = shadowRadius;

        entities.forEach(display -> DisplayEntityAccess.setShadowRadius(display, shadowRadius));
    }

    public float getShadowRadius() {
        return shadowRadius;
    }

    public void setShadowStrength(float shadowStrength) {
        this.shadowStrength = shadowStrength;

        entities.forEach(display -> DisplayEntityAccess.setShadowStrength(display, shadowStrength));
    }

    public float getShadowStrength() {
        return shadowStrength;
    }

    public void setDisplayWidth(float displayWidth) {
        this.displayWidth = displayWidth;

        entities.forEach(display -> DisplayEntityAccess.setDisplayWidth(display, displayWidth));
    }

    public float getDisplayWidth() {
        return displayWidth;
    }

    public void setDisplayHeight(float displayHeight) {
        this.displayHeight = displayHeight;

        entities.forEach(display -> DisplayEntityAccess.setDisplayHeight(display, displayHeight));
    }

    public float getDisplayHeight() {
        return displayHeight;
    }

    public void setGlowColorOverride(int glowColorOverride) {
        this.glowColorOverride = glowColorOverride;

        entities.forEach(display -> DisplayEntityAccess.setGlowColorOverride(display, glowColorOverride));
    }

    public int getGlowColorOverride() {
        return glowColorOverride;
    }

    @Override
    public Entity getEntity(ServerPlayerEntity player) {
        String language = translations.getLanguage(player);

        // one text display entity per language
        return entities.reference(language, lang -> {
            var textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            textDisplay.setPosition(position);

            DisplayEntityAccess.setText(textDisplay, text.translateTo(lang));
            DisplayEntityAccess.setLineWidth(textDisplay, lineWidth);
            DisplayEntityAccess.setTextOpacity(textDisplay, textOpacity);
            DisplayEntityAccess.setBackground(textDisplay, background);
            DisplayEntityAccess.setDisplayFlags(textDisplay, displayFlags);
            DisplayEntityAccess.setTransformation(textDisplay, transformation);
            DisplayEntityAccess.setInterpolationDuration(textDisplay, interpolationDuration);
            DisplayEntityAccess.setStartInterpolation(textDisplay, startInterpolation);
            DisplayEntityAccess.setBillboardMode(textDisplay, billboardMode);
            DisplayEntityAccess.setBrightness(textDisplay, brightness);
            DisplayEntityAccess.setViewRange(textDisplay, viewRange);
            DisplayEntityAccess.setShadowRadius(textDisplay, shadowRadius);
            DisplayEntityAccess.setShadowStrength(textDisplay, shadowStrength);
            DisplayEntityAccess.setDisplayWidth(textDisplay, displayWidth);
            DisplayEntityAccess.setDisplayHeight(textDisplay, displayHeight);
            DisplayEntityAccess.setGlowColorOverride(textDisplay, glowColorOverride);

            return textDisplay;
        });
    }

    @Override
    public void cleanup(ServerPlayerEntity player) {
        String language = translations.getLanguage(player);

        entities.dereference(language);
    }
}
