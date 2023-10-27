package work.lclpnet.ap2.impl.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.base.ApContainer;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.impl.prot.BasicProtector;
import work.lclpnet.lobby.game.impl.prot.MutableProtectionConfig;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultMiniGameHandle implements MiniGameHandle, Unloadable, WorldBorderManager {

    private final GameInfo info;
    private final PreparationActivity.Args args;
    private MutableProtectionConfig protectionConfig;
    private volatile BasicProtector protector = null;
    private WorldBorderListener worldBorderListener = null;

    public DefaultMiniGameHandle(GameInfo info, PreparationActivity.Args args) {
        this.info = info;
        this.args = args;
    }

    public void init() {
        ApContainer container = args.container();

        container.hookStack().push();
        container.schedulerStack().push();
    }

    @Override
    public MinecraftServer getServer() {
        return args.container().server();
    }

    @Override
    public GameInfo getGameInfo() {
        return info;
    }

    @Override
    public Logger getLogger() {
        return args.container().logger();
    }

    @Override
    public WorldFacade getWorldFacade() {
        return args.container().worldFacade();
    }

    @Override
    public MapFacade getMapFacade() {
        return args.container().mapFacade();
    }

    @Override
    public HookRegistrar getHookRegistrar() {
        return args.container().hookStack();
    }

    @Override
    public TaskScheduler getScheduler() {
        return args.container().schedulerStack();
    }

    @Override
    public TranslationService getTranslations() {
        return args.container().translationService();
    }

    @Override
    public Participants getParticipants() {
        return args.playerManager();
    }

    @Override
    public WorldBorderManager getWorldBorderManager() {
        return this;
    }

    @Override
    public synchronized void protect(Consumer<MutableProtectionConfig> action) {
        if (protector == null) {
            synchronized (this) {
                if (protector == null) {
                    protectionConfig = new MutableProtectionConfig();
                    protector = new BasicProtector(protectionConfig);
                }
            }
        }

        protector.deactivate();

        action.accept(protectionConfig);

        protector.activate();
    }

    @Override
    public void complete(Set<ServerPlayerEntity> winners) {
        // TODO track winners
        getLogger().info("Winners: {}", winners.stream()
                .map(ServerPlayerEntity::getEntityName)
                .collect(Collectors.toSet()));

        PreparationActivity activity = new PreparationActivity(args);
        ActivityManager.getInstance().startActivity(activity);
    }

    @Override
    public void unload() {
        ApContainer container = args.container();

        container.hookStack().pop();
        container.schedulerStack().pop();

        if (protector != null) {
            protector.unload();
        }
    }

    @Override
    public WorldBorder getWorldBorder() {
        return getServer().getOverworld().getWorldBorder();
    }

    @Override
    public void setupWorldBorder(ServerWorld world) {
        WorldBorder mainBorder = getServer().getOverworld().getWorldBorder();
        WorldBorder worldBorder = world.getWorldBorder();

        if (worldBorderListener != null) {
            mainBorder.removeListener(worldBorderListener);
        }

        worldBorderListener = new WorldBorderListener.WorldBorderSyncer(worldBorder);
        mainBorder.addListener(worldBorderListener);
    }

    @Override
    public void resetWorldBorder() {
        WorldBorder worldBorder = getServer().getOverworld().getWorldBorder();

        worldBorder.setCenter(0.5, 0.5);
        worldBorder.setSize(worldBorder.getMaxRadius());
        worldBorder.setSafeZone(5);
        worldBorder.setDamagePerBlock(0.2);
        worldBorder.setWarningTime(15);
        worldBorder.setWarningBlocks(5);

        if (worldBorderListener != null) {
            worldBorder.removeListener(worldBorderListener);
        }
    }
}
