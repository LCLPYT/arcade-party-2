package work.lclpnet.ap2.base.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;

import java.util.ArrayList;
import java.util.List;

public class GameDisplay {

    private final ScreenConfig config;
    private final ServerWorld world;
    private final HookRegistrar hooks;
    private final Translations translations;
    private final List<Entity> entities = new ArrayList<>();

    public GameDisplay(ScreenConfig config, ServerWorld world, HookRegistrar hooks, Translations translations) {
        this.config = config;
        this.world = world;
        this.hooks = hooks;
        this.translations = translations;
    }

    public void displayDefault() {
        clear();

        var title = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);

        entities.add(title);
        world.spawnEntity(title);
    }

    private void clear() {
        entities.forEach(Entity::discard);
        entities.clear();
    }
}
