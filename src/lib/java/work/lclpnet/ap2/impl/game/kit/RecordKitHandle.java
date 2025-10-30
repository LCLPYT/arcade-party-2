package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

public record RecordKitHandle(
        Identifier gameId, HookRegistrar hooks, TaskScheduler scheduler, Translations translations,
        DynamicRegistryManager registries, KitReadView readView
) implements KitHandle {

    public static RecordKitHandle of(MiniGameHandle gameHandle, DynamicRegistryManager registries, KitReadView readView) {
        return new RecordKitHandle(gameHandle.getGameInfo().getId(), gameHandle.getHooks(),
                gameHandle.getScheduler(), gameHandle.getTranslations(), registries, readView);
    }
}
