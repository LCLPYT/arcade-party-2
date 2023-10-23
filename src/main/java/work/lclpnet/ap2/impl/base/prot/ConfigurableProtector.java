package work.lclpnet.ap2.impl.base.prot;

import work.lclpnet.ap2.api.base.prot.ProtectionConfig;
import work.lclpnet.ap2.api.base.prot.ProtectionScope;
import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.Protector;
import work.lclpnet.kibu.plugin.hook.HookContainer;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.function.Consumer;

import static work.lclpnet.ap2.api.base.prot.ProtectionType.BREAK_BLOCKS;
import static work.lclpnet.ap2.api.base.prot.ProtectionType.PLACE_BLOCKS;

public class ConfigurableProtector implements Protector, Unloadable {

    private final ProtectionConfig config;
    private final HookContainer hooks;

    public ConfigurableProtector(ProtectionConfig config) {
        this.config = config;
        this.hooks = new HookContainer();
    }

    public void activate() {
        protect(BREAK_BLOCKS, scope -> {

        });

        protect(PLACE_BLOCKS, scope -> {

        });
    }

    @Override
    public void unload() {
        hooks.unload();
    }

    private void protect(ProtectionType type, Consumer<ProtectionScope> disallowedAction) {
        if (config.isAllowed(type)) return;

        disallowedAction.accept(null);  // TODO
    }
}
