package work.lclpnet.ap2.impl.scene;

import lombok.Getter;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

@Getter
public class TextDisplayObject extends DisplayEntityObject<DisplayEntity.TextDisplayEntity> {

    private Text text;

    public TextDisplayObject(Text text) {
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
    }

    @Override
    public TextDisplayObject deepCopy() {
        var copy = new TextDisplayObject(text);

        copy.deepCopy(this);

        return copy;
    }

    public void setText(Text text) {
        this.text = text;
        entityRef.optional().ifPresent(display -> display.setText(text));
    }
}
