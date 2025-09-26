package work.lclpnet.ap2.impl.scene.object;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.*;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.TranslatedTextDisplay;
import work.lclpnet.kibu.translate.Translations;

import java.util.Set;

import static work.lclpnet.ap2.impl.util.ThreadUtil.executeOn;

public class TranslatedTextDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable, DynamicEntity {

    private final Translations translations;
    private final TranslatedTextDisplay.ControllerImpl controller;
    @Getter
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private final Set<MountContext> contexts = new ObjectArraySet<>(1);
    private final Vector3d worldPos = new Vector3d(0);
    private Vec3d mcWorldPos = Vec3d.ZERO;

    public TranslatedTextDisplayObject(Scene scene, Translations translations) {
        super(scene);
        this.translations = translations;
        controller = new TranslatedTextDisplay.ControllerImpl(null);
    }

    public TranslatedTextDisplay.Controller controller() {
        return controller;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        updateWorldPos();

        if (transformer.update(matrixWorld)) {
            controller.getEntities().forEach(transformer::apply);
        }
    }

    private void updateWorldPos() {
        matrixWorld.transformPosition(worldPos.zero());

        if (worldPos.x != mcWorldPos.x || worldPos.y != mcWorldPos.y || worldPos.z != mcWorldPos.z) {
            mcWorldPos = new Vec3d(worldPos.x, worldPos.y, worldPos.z);
        }
    }

    @Override
    public Vec3d getPosition() {
        return mcWorldPos;
    }

    @Override
    public @Nullable Entity getEntity(ServerPlayerEntity player) {
        return controller.ref(translations.getLanguage(player), display -> {
            updateWorldPos();
            transformer.update(matrixWorld);
            transformer.apply(display);
        });
    }

    @Override
    public void cleanup(ServerPlayerEntity player) {
        controller.deref(translations.getLanguage(player));
    }

    @Override
    public void mount(MountContext ctx) {
        contexts.add(ctx);
        controller.setWorld(ctx.world());

        executeOn(ctx.world().getServer(), () -> ctx.spawn(null, this));
    }

    @Override
    public void unmount(MountContext ctx) {
        removeDisplay(ctx);
    }

    @Override
    protected void onDetached() {
        contexts.forEach(this::removeDisplay);
    }

    @Override
    public void updateTickRate(int tickRate) {
        controller.setInterpolationDuration(tickRate);
        controller.setTeleportDuration(tickRate);
    }

    private void removeDisplay(MountContext ctx) {
        ctx.remove(null, this);
        controller.getEntities().clear();
        contexts.remove(ctx);
    }
}
