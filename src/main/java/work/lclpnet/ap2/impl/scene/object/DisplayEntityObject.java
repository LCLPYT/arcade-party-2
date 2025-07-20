package work.lclpnet.ap2.impl.scene.object;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import net.minecraft.entity.decoration.DisplayEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Mountable;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Unmountable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;

import java.util.Set;

public abstract class DisplayEntityObject<T extends DisplayEntity> extends Object3d implements Mountable, Unmountable, Interpolatable {

    @Getter
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private final Set<MountContext> contexts = new ObjectArraySet<>(1);
    protected @NotNull Resolvable<T> entityRef = Resolvable.none();
    @Getter private boolean glowing = false;
    @Getter private int glowColorOverride = -1;
    @Getter private int interpolationDuration = 0;
    @Getter private int teleportDuration = 0;
    @Getter private DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;

    protected abstract @Nullable T createDisplayEntity(MountContext ctx);

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        entityRef.optional().ifPresent(display -> transformer.updateAndApply(display, matrixWorld));
    }

    @Override
    public void mount(MountContext ctx) {
        var display = createDisplayEntity(ctx);

        if (display == null) {
            entityRef = Resolvable.none();
            return;
        }

        configure(display);

        contexts.add(ctx);
        entityRef = ctx.spawn(display, this);
    }

    protected void configure(T display) {
        transformer.updateAndApply(display, matrixWorld);

        display.setGlowColorOverride(glowColorOverride);
        display.setInterpolationDuration(interpolationDuration);
        display.setTeleportDuration(teleportDuration);
        display.setBillboardMode(billboardMode);

        display.setGlowing(glowing);
    }

    @Override
    public void unmount(MountContext ctx) {
        removeDisplay(ctx);
    }

    @Override
    public void updateTickRate(int tickRate) {
        setInterpolationDuration(tickRate);
        setTeleportDuration(tickRate);
    }

    @Override
    protected void onDetached() {
        contexts.forEach(this::removeDisplay);
    }

    public void setGlowColorOverride(int glowColorOverride) {
        this.glowColorOverride = glowColorOverride;
        entityRef.optional().ifPresent(display -> display.setGlowColorOverride(glowColorOverride));
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        entityRef.optional().ifPresent(display -> display.setGlowing(glowing));
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;
        entityRef.optional().ifPresent(display -> display.setInterpolationDuration(interpolationDuration));
    }

    public void setTeleportDuration(int teleportDuration) {
        this.teleportDuration = teleportDuration;
        entityRef.optional().ifPresent(display -> display.setTeleportDuration(teleportDuration));
    }

    public void setBillboardMode(DisplayEntity.BillboardMode billboardMode) {
        this.billboardMode = billboardMode;
        entityRef.optional().ifPresent(display -> display.setBillboardMode(billboardMode));
    }

    protected void removeDisplay(MountContext ctx) {
        entityRef.optional().ifPresent(entity -> ctx.remove(entity, this));
        entityRef = Resolvable.none();
        contexts.remove(ctx);
    }
}
