package work.lclpnet.ap2.game.apocalypse_survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.NonNull;
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

    private List<MonsterSpawner<?>> spawners;
    private TargetManager targetManager;
    private int time = 0;

    public ApocalypseSurvivalInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        useTaskDisplay();
        useSmoothDeath();

        ServerLevel world = getWorld();
        GameMap map = getMap();
        Participants participants = gameHandle.getParticipants();

        Random random = new Random();

        targetManager = new TargetManager(participants, map, random);

        var setup = new AsSetup(map, world, random, targetManager);

        spawners = setup.readSpawners();

        commons().gameRuleBuilder()
                .set(GameRules.FALL_DAMAGE, true)
                .set(GameRules.MOB_GRIEFING, true)
                .set(GameRules.NATURAL_HEALTH_REGENERATION, false);

        HookRegistrar hooks = gameHandle.getHooks();


        ProjectileHooks.HIT_BLOCK.registerWith(hooks, (projectile, hit) -> {
            if (projectile instanceof AbstractArrow) {
                projectile.discard();
            }
        });

        ServerEntityHooks.ENTITY_LOAD.registerWith(hooks, (entity, relWorld) -> {
            if (relWorld != world) return;


            switch (entity) {
                case Zombie zombie -> targetManager.addZombie(zombie);
                case Skeleton skeleton -> targetManager.addSkeleton(skeleton);
                case Phantom phantom -> targetManager.addPhantom(phantom);
                case Vindicator vindicator -> targetManager.addVindicator(vindicator);
                case Vex vex -> VexEntityBehaviour.setForceClipping(vex, true);
                default -> {}
            }
        });

        ServerEntityHooks.ENTITY_UNLOAD.registerWith(hooks, (entity, relWorld) -> {
            if (relWorld == world && entity instanceof Mob mob) {
                targetManager.removeMob(mob);
            }
        });

        for (ServerPlayer player : participants) {
            PlayerReset.setAttribute(player, Attributes.SAFE_FALL_DISTANCE, 5.0);
            PlayerReset.setAttribute(player, Attributes.FALL_DAMAGE_MULTIPLIER, 0.5);
        }

        commons().displayHealth();
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, this::allowDamage);
            config.allow(ProtectionTypes.MOB_GRIEFING, ProtectionTypes.EXPLOSION);
        });

        gameHandle.getScheduler().interval(this::tick, 1);
    }

    @Override
    public void participantRemoved(@NonNull ServerPlayer player) {
        targetManager.removeParticipant(player);

        // put time survived msg
        Translations translations = gameHandle.getTranslations();
        int timeSurvived = time / 20;
        var duration = TimeHelper.formatTime(translations, timeSurvived);
        var detail = translations.translateText("game.ap2.cozy_campfire.survived", duration);
        getData().add(player, detail);

        super.participantRemoved(player);
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        if (entity instanceof Mob && (source.is(DamageTypes.FALL) || source.getDirectEntity() instanceof Projectile)) {
            return false;
        }

        return !source.is(DamageTypes.PLAYER_ATTACK);
    }

    private void tick() {
        int t = time++;

        spawners.forEach(MonsterSpawner::tick);

        if (t % 20 == 0) {
            targetManager.update();
        }
    }
}
