package work.lclpnet.ap2.game.apocalypse_survival;

import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.apocalypse_survival.util.AsSetup;
import work.lclpnet.ap2.game.apocalypse_survival.util.MonsterSpawner;
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.TimeHelper;
import work.lclpnet.kibu.behaviour.entity.VexEntityBehaviour;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.util.PlayerReset;

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
        useSmoothDeath();

        ServerWorld world = getWorld();
        GameMap map = getMap();
        Participants participants = gameHandle.getParticipants();

        Random random = new Random();

        targetManager = new TargetManager(participants, map, random);

        var setup = new AsSetup(map, world, random, targetManager);

        spawners = setup.readSpawners();

        commons().gameRuleBuilder()
                .set(GameRules.FALL_DAMAGE, true)
                .set(GameRules.DO_MOB_GRIEFING, true)
                .set(GameRules.NATURAL_REGENERATION, false);

        HookRegistrar hooks = gameHandle.getHookRegistrar();


        hooks.registerHook(ProjectileHooks.HIT_BLOCK, (projectile, hit) -> {
            if (projectile instanceof PersistentProjectileEntity) {
                projectile.discard();
            }
        });

        hooks.registerHook(ServerEntityHooks.ENTITY_LOAD, (entity, relWorld) -> {
            if (relWorld != world) return;


            switch (entity) {
                case ZombieEntity zombie -> targetManager.addZombie(zombie);
                case SkeletonEntity skeleton -> targetManager.addSkeleton(skeleton);
                case PhantomEntity phantom -> targetManager.addPhantom(phantom);
                case VindicatorEntity vindicator -> targetManager.addVindicator(vindicator);
                case VexEntity vex -> VexEntityBehaviour.setForceClipping(vex, true);
                default -> {}
            }
        });

        hooks.registerHook(ServerEntityHooks.ENTITY_UNLOAD, (entity, relWorld) -> {
            if (relWorld == world && entity instanceof MobEntity mob) {
                targetManager.removeMob(mob);
            }
        });

        for (ServerPlayerEntity player : participants) {
            PlayerReset.setAttribute(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 5.0);
            PlayerReset.setAttribute(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 0.5);
        }
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

        // put time survived msg
        Translations translations = gameHandle.getTranslations();
        int timeSurvived = time / 20;
        var duration = TimeHelper.formatTime(translations, timeSurvived);
        var detail = translations.translateText("game.ap2.cozy_campfire.survived", duration);
        getData().eliminated(player, detail);

        super.participantRemoved(player);
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        if (entity instanceof MobEntity && (source.isOf(DamageTypes.FALL) || source.getSource() instanceof ProjectileEntity)) {
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
