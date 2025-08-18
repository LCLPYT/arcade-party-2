package work.lclpnet.ap2.impl.scene.object;

import lombok.Getter;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Scene;

@Getter
public class TextDisplayObject extends DisplayEntityObject<DisplayEntity.TextDisplayEntity> {

    private Text text;
    private int background = 1073741824;

    public TextDisplayObject(Scene scene, Text text) {
        super(scene);
        this.text = text;
    }

    @Override
    protected @Nullable DisplayEntity.TextDisplayEntity createDisplayEntity(MountContext ctx) {
        return new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, ctx.world());
    }

    @Override
    protected void configure(DisplayEntity.TextDisplayEntity display) {
        super.configure(display);

        display.setText(text);
        display.setBackground(background);
    }

    @Override
    public TextDisplayObject deepCopy(Scene scene) {
        var copy = new TextDisplayObject(scene, text);

        copy.deepCopy(this);

        return copy;
    }

    public void setText(Text text) {
        this.text = text;
        entityRef.optional().ifPresent(display -> display.setText(text));
    }

    public void setBackground(int background) {
        this.background = background;
        entityRef.optional().ifPresent(display -> display.setBackground(background));
    }
}
