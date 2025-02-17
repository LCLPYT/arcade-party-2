package work.lclpnet.ap2.impl.scene;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntity;
import work.lclpnet.ap2.impl.util.world.entity.TranslatedTextDisplay;
import work.lclpnet.kibu.translate.Translations;

import java.util.Set;

public class TranslatedTextDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable, DynamicEntity {

    private final Translations translations;
    private final TranslatedTextDisplay.ControllerImpl controller;
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private final Set<MountContext> contexts = new ObjectArraySet<>(1);
    private Vec3d pos = Vec3d.ZERO;

    public TranslatedTextDisplayObject(Translations translations) {
        this.translations = translations;
        controller = new TranslatedTextDisplay.ControllerImpl(null);
    }

    public TranslatedTextDisplay.Controller controller() {
        return controller;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        pos = new Vec3d(position.x, position.y, position.z);
        transformer.update(matrixWorld, position.x, position.y, position.z);
        controller.getEntities().forEach(display -> transformer.applyTransformation(display));
    }

    @Override
    public Vec3d getPosition() {
        return pos;
    }

    @Override
    public @Nullable Entity getEntity(ServerPlayerEntity player) {
        return controller.ref(translations.getLanguage(player));
    }

    @Override
    public void cleanup(ServerPlayerEntity player) {
        controller.deref(translations.getLanguage(player));
    }

    @Override
    public void mount(MountContext ctx) {
        contexts.add(ctx);
        controller.setWorld(ctx.world());
        ctx.spawn(null, this);
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
    }

    private void removeDisplay(MountContext ctx) {
        ctx.remove(null, this);
        controller.getEntities().clear();
        contexts.remove(ctx);
    }
}
