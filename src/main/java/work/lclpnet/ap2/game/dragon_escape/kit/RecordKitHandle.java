package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;

public record RecordKitHandle(
        Identifier gameId, HookRegistrar hooks, Translations translations, DynamicRegistryManager registries
) implements KitHandle {

    public static RecordKitHandle of(MiniGameHandle gameHandle, DynamicRegistryManager registries) {
        return new RecordKitHandle(gameHandle.getGameInfo().getId(), gameHandle.getHookRegistrar(), gameHandle.getTranslations(), registries);
    }
}
