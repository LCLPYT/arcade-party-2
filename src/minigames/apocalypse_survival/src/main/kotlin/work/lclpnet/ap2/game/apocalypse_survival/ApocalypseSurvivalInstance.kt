package work.lclpnet.ap2.game.apocalypse_survival

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.apocalypse_survival.util.AsSetup
import work.lclpnet.ap2.game.apocalypse_survival.util.MonsterSpawner
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.useTaskDisplay
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.PlayerReset
import work.lclpnet.kibu.behaviour.entity.VexEntityBehaviour
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerEntityHooks
import java.util.*

class ApocalypseSurvivalInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map) {

    private val random = Random()
    private val targetManager = TargetManager(players(), map, random)
    private val stats = createStats(CommonStats.DistanceMoved, CommonStats.TimeSurvived)
    private lateinit var spawners: List<MonsterSpawner>
    private var time = 0

    override fun prepare() {
        useTaskDisplay()
        useSmoothDeath()
        trackSurvivalTime(stats)

        val setup = AsSetup(map, level, random, targetManager)
        spawners = setup.readSpawners()

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, true)
            .set(GameRules.MOB_GRIEFING, true)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, false)

        val hooks = gameHandle.hooks

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ ->
            if (projectile is AbstractArrow) {
                projectile.discard()
            }
        }

        ServerEntityHooks.ENTITY_LOAD.registerWith(hooks) { entity, relWorld ->
            if (relWorld != level) return@registerWith

            when (entity) {
                is Zombie -> targetManager.addZombie(entity)
                is Skeleton -> targetManager.addSkeleton(entity)
                is Phantom -> targetManager.addPhantom(entity)
                is Vindicator -> targetManager.addVindicator(entity)
                is Vex -> VexEntityBehaviour.setForceClipping(entity, true)
            }
        }

        ServerEntityHooks.ENTITY_UNLOAD.registerWith(hooks) { entity, relWorld ->
            if (relWorld == level && entity is Mob) {
                targetManager.removeMob(entity)
            }
        }

        trackDistanceMoved(stats)

        for (player in players()) {
            PlayerReset.setAttribute(player, Attributes.SAFE_FALL_DISTANCE, 5.0)
            PlayerReset.setAttribute(player, Attributes.FALL_DAMAGE_MULTIPLIER, 0.5)
        }

        commons().displayHealth()
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config, ::allowDamage)
            config.allow(ProtectionTypes.MOB_GRIEFING, ProtectionTypes.EXPLOSION)
        }

        runEveryTick { tick() }
    }

    override fun participantRemoved(player: net.minecraft.server.level.ServerPlayer) {
        targetManager.removeParticipant(player)

        val translations = gameHandle.translations
        val timeSurvived = time / 20
        val duration = TimeHelper.formatTime(translations, timeSurvived)
        val detail = translations.translateText("game.ap2.apocalypse_survival.survived", duration)
        data.add(player, detail)

        super.participantRemoved(player)
    }

    private fun allowDamage(entity: Entity, source: DamageSource): Boolean {
        if (entity is Mob && (source.isOf(DamageTypes.FALL) || source.directEntity is Projectile)) {
            return false
        }

        return !source.isOf(DamageTypes.PLAYER_ATTACK)
    }

    private fun tick() {
        val t = time++

        spawners.forEach(MonsterSpawner::tick)

        if (t % 20 == 0) {
            targetManager.update()
        }
    }
}
