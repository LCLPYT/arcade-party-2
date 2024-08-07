package work.lclpnet.ap2.game.apocalypse_survival;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.apocalypse_survival.util.AsSetup;
import work.lclpnet.ap2.game.apocalypse_survival.util.MonsterSpawner;
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;

public class ApocalypseSurvivalInstance extends EliminationGameInstance {

    private List<MonsterSpawner> spawners;
    private TargetManager targetManager;
    private int time = 0;

    public ApocalypseSurvivalInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        useTaskDisplay();
        useNoHealing();
        useSmoothDeath();

        ServerWorld world = getWorld();
        GameMap map = getMap();
        MinecraftServer server = gameHandle.getServer();

        CustomScoreboardManager debug$scoreboardManager = gameHandle.getScoreboardManager();

        Team debug$pursuitTeam = debug$scoreboardManager.createTeam("pursuit");
        debug$pursuitTeam.setColor(Formatting.DARK_RED);

        Team debug$roamTeam = debug$scoreboardManager.createTeam("roam");
        debug$roamTeam.setColor(Formatting.BLUE);

        targetManager = new TargetManager(gameHandle.getParticipants(), map,
                debug$scoreboardManager, debug$pursuitTeam, debug$roamTeam);

        var setup = new AsSetup(map, world, new Random(), targetManager);

        spawners = setup.readSpawners();

        GameRules gameRules = world.getGameRules();
        gameRules.get(GameRules.FALL_DAMAGE).set(true, server);
        gameRules.get(GameRules.DO_MOB_GRIEFING).set(true, server);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ServerEntityHooks.ENTITY_UNLOAD, (entity, relWorld) -> {
            if (relWorld == world && entity instanceof MobEntity mob) {
                targetManager.removeMob(mob);
            }
        });

        hooks.registerHook(ProjectileHooks.HIT_BLOCK, (projectile, hit) -> projectile.discard());
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, this::allowDamage);
            config.allow(ProtectionTypes.MOB_GRIEFING, ProtectionTypes.EXPLOSION);
        });

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        targetManager.removeParticipant(player);
        super.participantRemoved(player);
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        if (source.isOf(DamageTypes.FALL) && entity instanceof MobEntity) {
            return false;
        }

        return !source.isOf(DamageTypes.PLAYER_ATTACK);
    }

    private void tick() {
        int t = time++;

        spawners.forEach(MonsterSpawner::tick);

        if (t % 20 == 0) {
            targetManager.update();
        }
    }
}
